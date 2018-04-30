package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.springframework.core.io.ByteArrayResource;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("ConstantConditions")
public class ConsentServiceMockTest {
    private static final Withdrawal WITHDRAWAL = new Withdrawal("For reasons.");
    private static final String EXTERNAL_ID = "external-id";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long SIGNED_ON = 1446044925219L;
    private static final long WITHDREW_ON = SIGNED_ON + 10000;
    private static final long CONSENT_CREATED_ON = 1446044814108L;
    private static final String ID = "user-id";
    private static final String HEALTH_CODE = "health-code";
    private static final String EMAIL = "email@email.com";
    private static final SubpopulationGuid SECOND_SUBPOP = SubpopulationGuid.create("anotherSubpop");
    private ConsentService consentService;

    @Mock
    private AccountDao accountDao;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private StudyConsentService studyConsentService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private Subpopulation subpopulation;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> emailCaptor;
    @Captor
    private ArgumentCaptor<WithdrawConsentEmailProvider> withdrawalProviderCaptor;
    
    private String documentString;
        
    private CriteriaContext context;
    private Study study;
    private StudyParticipant participant;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() throws IOException {
        documentString = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
                
        consentService = new ConsentService();
        consentService.setAccountDao(accountDao);
        consentService.setSendMailService(sendMailService);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setSubpopulationService(subpopService);
        consentService.setConsentTemplate(new ByteArrayResource((documentString).getBytes()));

        study = TestUtils.getValidStudy(ConsentServiceMockTest.class);
        study.setConsentNotificationEmailVerified(true);
        study.setSignedConsentTemplate(new EmailTemplate("Subject", "Body", MimeType.HTML));

        participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).withId(ID).withEmail(EMAIL)
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withExternalId(EXTERNAL_ID).build();
        
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(SIGNED_ON).build();
        
        account = spy(new GenericAccount()); // mock(Account.class);
        ((GenericAccount)account).setId(ID);
        when(accountDao.getAccount(any(AccountId.class))).thenReturn(account);
        
        StudyConsentView studyConsentView = mock(StudyConsentView.class);
        when(studyConsentView.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentView.getDocumentContent()).thenReturn("<p>This is content of the final HTML document we assemble.</p>");
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(studyConsentView);
        when(subpopService.getSubpopulation(study.getStudyIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);

        // Set up criteria context.
        context = new CriteriaContext.Builder().withUserId(participant.getId())
                .withStudyIdentifier(study.getStudyIdentifier()).build();
    }
    
    @Test
    public void userCannotGetConsentForSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        when(subpopService.getSubpopulation(study, badGuid)).thenThrow(new EntityNotFoundException(Subpopulation.class));
        try {
            consentService.getConsentSignature(study, SubpopulationGuid.create("not-correct"), participant.getId());
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void userCannotConsentToSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        doThrow(new EntityNotFoundException(Subpopulation.class)).when(subpopService).getSubpopulation(study.getStudyIdentifier(), badGuid);
        try {
            consentService.consentToResearch(study, badGuid, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void giveConsentSuccess() {
        StudyConsent consent = mock(StudyConsent.class);
        doReturn(CONSENT_CREATED_ON).when(consent).getCreatedOn();
        
        StudyConsentView view = mock(StudyConsentView.class);
        when(view.getStudyConsent()).thenReturn(consent);
        when(view.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(view.getDocumentContent()).thenReturn(documentString);
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(view);

        // Account already has a withdrawn consent, to make sure we're correctly appending consents.
        ConsentSignature withdrawnSig = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(withdrawnSig));

        // Consent signature should have a withdrawOn and a dummy consentCreatedOn just to make sure that we're setting
        // those properly in ConsentService.
        ConsentSignature sig = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withConsentCreatedOn(CONSENT_CREATED_ON + 20000).withWithdrewOn(12345L).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, true);

        // verify consents were set on account properly
        ArgumentCaptor<Account> updatedAccountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountDao).updateAccount(updatedAccountCaptor.capture(), eq(false));

        Account updatedAccount = updatedAccountCaptor.getValue();
        List<ConsentSignature> updatedConsentList = updatedAccount.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(2, updatedConsentList.size());

        // First consent is the same.
        assertEquals(withdrawnSig, updatedConsentList.get(0));

        // Second consent has consentCreatedOn added and withdrawnOn clear, but is otherwise the same.
        assertEquals(sig.getBirthdate(), updatedConsentList.get(1).getBirthdate());
        assertEquals(sig.getName(), updatedConsentList.get(1).getName());
        assertEquals(sig.getSignedOn(), updatedConsentList.get(1).getSignedOn());
        assertEquals(CONSENT_CREATED_ON, updatedConsentList.get(1).getConsentCreatedOn());
        assertNull(updatedConsentList.get(1).getWithdrewOn());

        // Consent we send to activityEventService is same as the second consent.
        verify(activityEventService).publishEnrollmentEvent(study, participant.getHealthCode(), updatedConsentList.get(1));

        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
        assertTrue(recipients.contains(participant.getEmail()));
    }

    @Test
    public void emailConsentAgreementSuccess() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        assertEquals(2, emailCaptor.getValue().getRecipientEmails().size());
        assertTrue(emailCaptor.getValue().getRecipientEmails().contains(study.getConsentNotificationEmail()));
        assertTrue(emailCaptor.getValue().getRecipientEmails().contains(participant.getEmail()));
    }
    
    @Test
    public void noActivityEventIfTooYoung() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        study.setMinAgeOfConsent(30); // Test is good until 2044. So there.
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfAlreadyConsented() {
        ConsentSignature sig = new ConsentSignature.Builder().withConsentCreatedOn(CONSENT_CREATED_ON)
                .withSignedOn(DateTime.now().getMillis()).withName("A Name").withBirthdate("1960-10-10").build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(sig));

        try {
            consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfDaoFails() {
        try {
            consentService.consentToResearch(study, SubpopulationGuid.create("badGuid"), participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void withdrawConsentWithParticipant() throws Exception {
        account.setEmail(EMAIL);

        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(AccountId.forId(study.getIdentifier(), participant.getId()));

        // Add two consents to the account, one withdrawn, one active. This tests to make sure we're not accidentally
        // dropping withdrawn consents from the history.
        ConsentSignature withdrawnConsent = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(withdrawnConsent, consentSignature));

        // Execute and validate.
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL,
                SIGNED_ON + 10000);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        
        verify(accountDao).getAccount(context.getAccountId());
        verify(accountDao).updateAccount(captor.capture(), eq(false));
        // It happens twice because we do it the first time to set up the test properly
        //verify(account, times(2)).getConsentSignatures(setterCaptor.capture());
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        Account account = captor.getValue();

        // Both signatures are there, and the second one is now withdrawn.
        assertNull(account.getActiveConsentSignature(SUBPOP_GUID));

        List<ConsentSignature> updatedConsentList = account.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(2, updatedConsentList.size());

        // First consent is unchanged.
        assertEquals(withdrawnConsent, updatedConsentList.get(0));

        // Second consent has withdrawnOn tacked on, but is otherwise the same.
        assertEquals(consentSignature.getBirthdate(), updatedConsentList.get(1).getBirthdate());
        assertEquals(consentSignature.getName(), updatedConsentList.get(1).getName());
        assertEquals(consentSignature.getSignedOn(), updatedConsentList.get(1).getSignedOn());
        assertEquals(SIGNED_ON + 10000, updatedConsentList.get(1).getWithdrewOn().longValue());

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("\"Test Study [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceMockTest]", email.getSubject());
        assertEquals("<p>User   &lt;" + EMAIL + "&gt; (external ID: " + EXTERNAL_ID +
                        ")  withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>",
                email.getMessageParts().get(0).getContent());
    }
    
    @Test
    public void withdrawConsentWithAccount() throws Exception {
        setupWithdrawTest();
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, SIGNED_ON);
        
        verify(accountDao).updateAccount(account, false);
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));

        // Contents of call are tested in prior test where participant is used
    }

    @Test
    public void withdrawAllConsentsWithEmail() throws Exception {
        setupWithdrawTest();
        TestUtils.mockEditAccount(accountDao, account);
        
        Withdrawal withdrawal = WITHDRAWAL;
        consentService.withdrawAllConsents(study, participant, context, withdrawal, SIGNED_ON);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
        verify(account).setSharingScope(SharingScope.NO_SHARING);

        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();

        assertEquals("\"Test Study [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceMockTest]", email.getSubject());
        assertEquals("<p>User   &lt;" + EMAIL + "&gt; (external ID: " + EXTERNAL_ID +
                        ")  withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>",
                email.getMessageParts().get(0).getContent());

        Account updatedAccount = accountCaptor.getValue();
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }
    }
    
    @Test
    public void withdrawAllConsentsWithPhone() {
        TestUtils.mockEditAccount(accountDao, account);
        account.setPhone(TestConstants.PHONE);

        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(AccountId.forId(study.getIdentifier(), participant.getId()));

        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        Withdrawal withdrawal = WITHDRAWAL;
        
        consentService.withdrawAllConsents(study, participant, context, withdrawal, SIGNED_ON);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
        verify(account).setSharingScope(SharingScope.NO_SHARING);
        verify(sendMailService, never()).sendEmail(any(MimeTypeEmailProvider.class));
        
        Account updatedAccount = accountCaptor.getValue();
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }
    }
    
    @Test
    public void accountFailureConsistent() {
        when(accountDao.getAccount(any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL,
                    DateTime.now().getMillis());
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
            // expected exception
        }
        verifyNoMoreInteractions(sendMailService);
    }

    @Test
    public void withdrawWithNotificationEmailVerifiedNull() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(null);
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context,
                WITHDRAWAL, SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(false);
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context,
                WITHDRAWAL, SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawAllWithNotificationEmailVerifiedNull() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(null);
        consentService.withdrawAllConsents(study, participant, context, WITHDRAWAL,
                SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawAllWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(false);
        consentService.withdrawAllConsents(study, participant, context, WITHDRAWAL,
                SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void withdrawFromOneRequiredConsentSetsAccountToNoSharing() throws Exception {
        setupWithdrawTest(true, false);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());
    }

    @Test
    public void withdrawFromOneOfTwoRequiredConsentsSetsAcountToNoSharing() throws Exception {
        setupWithdrawTest(true, true);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        // You must sign all required consents to sign.
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());
    }
    
    @Test
    public void withdrawFromOneOptionalConsentDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, true);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        // Not changed because all the required consents are still signed.
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());
    }
    
    @Test
    public void withdrawFromOneOfTwoOptionalConsentsDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, false);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        // Not changed because all the required consents are still signed.
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());
    }
    
    @Test
    public void consentToResearchNoConsentAdministratorEmail() {
        study.setConsentNotificationEmail(null);
        
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(participant.getEmail()));
    }
    
    @Test
    public void consentToResearchSuppressEmailNotification() {
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
    }
    
    @Test
    public void consentToResearchNoParticipantEmail() {
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(participant).withEmail(null).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, noEmail, consentSignature, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
    }

    @Test
    public void consentToResearchNoRecipients() {
        // easiest to test this if we null out the study consent email.
        study.setConsentNotificationEmail(null);
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        // sendEmail = true because the system would otherwise send it based on the call, but hasn't looked to suppress yet.
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature,
                SharingScope.ALL_QUALIFIED_RESEARCHERS, true);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void consentToResearchSucceedsWithoutNotification() {
        // In this call, we explicitly override any other settings to suppress notifications
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void withdrawConsentNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        study.setConsentNotificationEmail(null);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawAllConsentsNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        study.setConsentNotificationEmail(null);
        
        consentService.withdrawAllConsents(study, participant, context, WITHDRAWAL, WITHDREW_ON);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void emailConsentAgreementNoConsentAdministratorEmail() {
        study.setConsentNotificationEmail(null);
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        assertEquals(1, emailCaptor.getValue().getRecipientEmails().size());
        assertEquals(participant.getEmail(), emailCaptor.getValue().getRecipientEmails().iterator().next());
    }
    
    @Test
    public void emailConsentAgreementDoesNotSuppressEmailNotification() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        // Despite explicitly suppressing email, if the user makes this call, we will send the email.
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
        assertTrue(recipients.contains(participant.getEmail()));
    }
    
    @Test
    public void emailConsentAgreementNoParticipantEmail() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(participant).withEmail(null).build();
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, noEmail);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();        
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
    }

    @Test
    public void emailConsentAgreementNoRecipients() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        // easiest to test this if we null out the study consent email.
        study.setConsentNotificationEmail(null);
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(participant).withEmail(null).build();
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, noEmail);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    // Tests of the construction of recipients for email, originally part of special email builder.

    @Test
    public void consentNotificationEmailVerifiedNull() throws Exception {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        // For backwards-compatibility, consentNotificationEmailVerified=null means we still send it to the consent
        // notification email.
        study.setConsentNotificationEmailVerified(null);

        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);
    }

    @Test
    public void consentNotificationEmailVerifiedFalse() throws Exception {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));

        study.setConsentNotificationEmailVerified(false);

        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate email recipients does not include consent notification email.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(1, recipientList.size());
        assertEquals("email@email.com", recipientList.get(0));
    }
    
    @Test
    public void consentCanHandleNullConsentEmail() throws Exception {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        study.setConsentNotificationEmail(null);
        
        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(1, recipientList.size());
        assertEquals("email@email.com", recipientList.get(0));
    }
    
    @Test
    public void providerWithoutRecipientsWorks() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        
        study.setConsentNotificationEmail(null);
        
        // The provider reports that there are no addresses to send to, which is correct
        participant = new StudyParticipant.Builder().copyOf(participant).withEmail(null).build();
        consentService.emailConsentAgreement(study, SUBPOP_GUID, participant);
        
        verify(sendMailService, never()).sendEmail(emailCaptor.capture());
        /* TODO
        provider = new ConsentEmailProvider(study, participant.getEmail(), BYTE_ARRAY);
        assertFalse(provider.getRecipientEmails().isEmpty());
        
        study.setConsentNotificationEmail("email@email.com");
        provider = new ConsentEmailProvider(study, participant.getEmail(), BYTE_ARRAY);
        assertFalse(provider.getRecipients().isEmpty());
        */
    }

    private static void validateCommonElements(MimeTypeEmail email) {
        assertEquals("Subject", email.getSubject());
        assertEquals("\"Test Study [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>",
                email.getSenderAddress());
        assertEquals(Sets.newHashSet("email@email.com","bridge-testing+consent@sagebase.org"),
                Sets.newHashSet(email.getRecipientAddresses()));
    }    
    
    private void setupWithdrawTest(boolean subpop1Required, boolean subpop2Required) {
        // two consents, withdrawing one does not turn sharing entirely off.
        account.setEmail(EMAIL);
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(subpop1Required);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(subpop2Required);
        
        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(AccountId.forId(study.getIdentifier(), participant.getId()));
        doReturn(ImmutableList.of(subpop1, subpop2)).when(subpopService).getSubpopulationsForUser(any());
        
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withSignedOn(SIGNED_ON).build();
        List<ConsentSignature> consents = ImmutableList.of(consentSignature);
        
        ConsentSignature secondConsentSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withSignedOn(SIGNED_ON).build();
        List<ConsentSignature> secondConsents = ImmutableList.of(secondConsentSignature);
        
        account.setConsentSignatureHistory(SUBPOP_GUID, consents);
        account.setConsentSignatureHistory(SECOND_SUBPOP, secondConsents);
    }
    
    private void setupWithdrawTest() {
        account.setEmail(EMAIL);

        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(AccountId.forId(study.getIdentifier(), participant.getId()));

        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
    }
}