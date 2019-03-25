package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ObjectMetadata;
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
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import org.springframework.core.io.ByteArrayResource;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("ConstantConditions")
public class ConsentServiceTest {
    private static final String SHORT_URL = "https://ws.sagebridge.org/r/XXXXX";
    private static final String LONG_URL = "http://sagebionetworks.org/platforms/";
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
    private static final ConsentSignature CONSENT_SIGNATURE = new ConsentSignature.Builder().withName("Test User")
            .withBirthdate("1980-01-01").withSignedOn(SIGNED_ON).build();
    private static final ConsentSignature WITHDRAWN_CONSENT_SIGNATURE = new ConsentSignature.Builder()
            .withConsentSignature(CONSENT_SIGNATURE).withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000)
            .build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .withId(ID).withEmail(EMAIL).withEmailVerified(Boolean.TRUE)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withExternalId(EXTERNAL_ID).build();
    private static final StudyParticipant PHONE_PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .withId(ID).withPhone(TestConstants.PHONE).withPhoneVerified(Boolean.TRUE)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withExternalId(EXTERNAL_ID).build();
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder().withUserId(PARTICIPANT.getId())
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();

    @Spy
    private ConsentService consentService;
    
    private Study study;

    @Mock
    private AccountDao accountDao;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private SmsService smsService;
    @Mock
    private StudyConsentService studyConsentService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private NotificationsService notificationsService;
    @Mock
    private S3Helper s3Helper;
    @Mock
    private UrlShortenerService urlShortenerService; 
    @Mock
    private Subpopulation subpopulation;
    @Mock
    private StudyConsentView studyConsentView;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> emailCaptor;
    @Captor
    private ArgumentCaptor<SmsMessageProvider> smsProviderCaptor;
    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private Account account;
    
    @Before
    public void before() throws IOException {
        String documentString = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
        
        consentService.setAccountDao(accountDao);
        consentService.setSendMailService(sendMailService);
        consentService.setActivityEventService(activityEventService);
        consentService.setSmsService(smsService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setSubpopulationService(subpopService);
        consentService.setS3Helper(s3Helper);
        consentService.setUrlShortenerService(urlShortenerService);
        consentService.setNotificationsService(notificationsService);
        consentService.setConsentTemplate(new ByteArrayResource((documentString).getBytes()));
        
        study = TestUtils.getValidStudy(ConsentServiceTest.class);

        account = Account.create();
        account.setId(ID);
        
        when(accountDao.getAccount(any(AccountId.class))).thenReturn(account);
        
        when(s3Helper.generatePresignedUrl(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), any(), any(),
                eq(HttpMethod.GET))).thenReturn(new URL(LONG_URL));
        when(urlShortenerService.shortenUrl(LONG_URL, BridgeConstants.SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS))
            .thenReturn(SHORT_URL);
        
        when(studyConsentView.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentView.getDocumentContent()).thenReturn("<p>This is content of the final HTML document we assemble.</p>");
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(studyConsentView);
        when(subpopService.getSubpopulation(study.getStudyIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void userCannotGetConsentSignatureForSubpopulationToWhichTheyAreNotMapped() {
        when(subpopService.getSubpopulation(study, SUBPOP_GUID)).thenThrow(new EntityNotFoundException(Subpopulation.class));
        
        consentService.getConsentSignature(study, SUBPOP_GUID, PARTICIPANT.getId());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void userCannotGetConsentSignatureWithNoActiveSig() {
        // set signatures in the account... but not the right signatures
        account.setConsentSignatureHistory(SubpopulationGuid.create("second-subpop"), ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.getConsentSignature(study, SUBPOP_GUID, PARTICIPANT.getId());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void userCannotConsentToSubpopulationToWhichTheyAreNotMapped() {
        when(subpopService.getSubpopulation(study.getStudyIdentifier(), SUBPOP_GUID)).thenThrow(new EntityNotFoundException(Subpopulation.class));

        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, false);
    }
    
    @Test
    public void giveConsentSuccess() {
        // Account already has a withdrawn consent, to make sure we're correctly appending consents.
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(WITHDRAWN_CONSENT_SIGNATURE));

        // Consent signature should have a withdrawOn and a dummy consentCreatedOn just to make sure that we're setting
        // those properly in ConsentService.
        ConsentSignature sig = new ConsentSignature.Builder().withConsentSignature(CONSENT_SIGNATURE)
                .withConsentCreatedOn(CONSENT_CREATED_ON + 20000).withWithdrewOn(12345L).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);

        // verify consents were set on account properly
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));

        Account updatedAccount = accountCaptor.getValue();
        List<ConsentSignature> updatedConsentList = updatedAccount.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(2, updatedConsentList.size());

        // First consent is the same.
        assertEquals(WITHDRAWN_CONSENT_SIGNATURE, updatedConsentList.get(0));

        // Second consent has consentCreatedOn added and withdrawnOn clear, but is otherwise the same.
        assertEquals(sig.getBirthdate(), updatedConsentList.get(1).getBirthdate());
        assertEquals(sig.getName(), updatedConsentList.get(1).getName());
        assertEquals(sig.getSignedOn(), updatedConsentList.get(1).getSignedOn());
        assertEquals(CONSENT_CREATED_ON, updatedConsentList.get(1).getConsentCreatedOn());
        assertNull(updatedConsentList.get(1).getWithdrewOn());

        // Consent we send to activityEventService is same as the second consent.
        verify(activityEventService).publishEnrollmentEvent(study, PARTICIPANT.getHealthCode(), updatedConsentList.get(1));

        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        // We notify the study administrator and send a copy to the user.
        BasicEmailProvider email = emailCaptor.getValue();
        assertEquals(EmailType.SIGN_CONSENT, email.getType());

        Set<String> recipients = email.getRecipientEmails();
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }
    
    @Test
    public void getConsentStatuses() throws Exception {
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(CONSENT_SIGNATURE));
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(true);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(true);
        when(subpopService.getSubpopulationsForUser(CONTEXT)).thenReturn(ImmutableList.of(subpop1, subpop2));

        Map<SubpopulationGuid,ConsentStatus> statusMap = consentService.getConsentStatuses(CONTEXT);
        
        ConsentStatus status1 = statusMap.get(SUBPOP_GUID);
        assertFalse(status1.isConsented());
        
        ConsentStatus status2 = statusMap.get(SECOND_SUBPOP);
        assertTrue(status2.isConsented());
    }

    @Test
    public void emailConsentAgreementSuccess() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, PARTICIPANT);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        BasicEmailProvider email = emailCaptor.getValue();
        assertEquals(1, email.getRecipientEmails().size());
        assertTrue(email.getRecipientEmails().contains(PARTICIPANT.getEmail()));
        assertEquals(EmailType.RESEND_CONSENT, email.getType());
    }
    
    @Test
    public void noConsentIfTooYoung() {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withConsentSignature(CONSENT_SIGNATURE)
                .withBirthdate("2018-05-12").build();
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
            verifyNoMoreInteractions(accountDao);
        }
    }
    
    @Test
    public void noConsentIfAlreadyConsented() {
        ConsentSignature sig = new ConsentSignature.Builder().withConsentCreatedOn(CONSENT_CREATED_ON)
                .withSignedOn(DateTime.now().getMillis()).withName("A Name").withBirthdate("1960-10-10").build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(sig));

        try {
            consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
            verify(accountDao).getAccount(any());
            verifyNoMoreInteractions(accountDao);
        }
    }
    
    @Test
    public void noConsentIfDaoFails() {
        try {
            consentService.consentToResearch(study, SubpopulationGuid.create("badGuid"), PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
            verify(accountDao).getAccount(any());
            verifyNoMoreInteractions(accountDao);
        }
    }
    
    @Test
    public void withdrawConsentWithParticipant() throws Exception {
        account.setEmail(EMAIL);
        account.setHealthCode(PARTICIPANT.getHealthCode());

        // Add two consents to the account, one withdrawn, one active. This tests to make sure we're not accidentally
        // dropping withdrawn consents from the history.
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(WITHDRAWN_CONSENT_SIGNATURE, CONSENT_SIGNATURE));

        // Execute and validate.
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON + 10000);

        verify(accountDao).getAccount(CONTEXT.getAccountId());
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        Account account = accountCaptor.getValue();

        // Both signatures are there, and the second one is now withdrawn.
        assertNull(account.getActiveConsentSignature(SUBPOP_GUID));

        List<ConsentSignature> updatedConsentList = account.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(2, updatedConsentList.size());

        // First consent is unchanged.
        assertEquals(WITHDRAWN_CONSENT_SIGNATURE, updatedConsentList.get(0));

        // Second consent has withdrawnOn tacked on, but is otherwise the same.
        assertEquals(CONSENT_SIGNATURE.getBirthdate(), updatedConsentList.get(1).getBirthdate());
        assertEquals(CONSENT_SIGNATURE.getName(), updatedConsentList.get(1).getName());
        assertEquals(CONSENT_SIGNATURE.getSignedOn(), updatedConsentList.get(1).getSignedOn());
        assertEquals(SIGNED_ON + 10000, updatedConsentList.get(1).getWithdrewOn().longValue());

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("\"Test Study [ConsentServiceTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceTest]", email.getSubject());
        assertEquals("<p>User   &lt;" + EMAIL + "&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>",
                email.getMessageParts().get(0).getContent());
    }
    
    @Test
    public void withdrawConsentWithAccount() throws Exception {
        setupWithdrawTest();
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);
        
        verify(accountDao).updateAccount(account, null);
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));

        // Contents of call are tested in prior test where participant is used
    }

    @Test
    public void withdrawFromStudyWithEmail() throws Exception {
        setupWithdrawTest();
        
        consentService.withdrawFromStudy(study, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());

        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();

        assertEquals("\"Test Study [ConsentServiceTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceTest]", email.getSubject());
        assertEquals("<p>User Allen Wrench &lt;" + EMAIL + "&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>",
                email.getMessageParts().get(0).getContent());

        Account updatedAccount = accountCaptor.getValue();
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        assertNull(account.getFirstName());
        assertNull(account.getLastName());
        assertFalse(account.getNotifyByEmail());
        assertNull(account.getEmail());
        assertFalse(account.getEmailVerified());
        assertNull(account.getPhone());
        assertFalse(account.getPhoneVerified());
        assertEquals("externalId", account.getExternalId());
        // This association is not removed
        assertEquals(1, account.getAccountSubstudies().size());
        AccountSubstudy acctSubstudy = account.getAccountSubstudies().iterator().next();
        assertEquals("substudyId", acctSubstudy.getSubstudyId());
        assertEquals("anExternalId", acctSubstudy.getExternalId());
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }
    }
    
    @Test
    public void withdrawFromStudyWithPhone() {
        account.setPhone(TestConstants.PHONE);
        account.setHealthCode(PARTICIPANT.getHealthCode());
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.withdrawFromStudy(study, PHONE_PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        verify(sendMailService, never()).sendEmail(any(MimeTypeEmailProvider.class));
        
        Account updatedAccount = accountCaptor.getValue();
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }

        verify(notificationsService).deleteAllRegistrations(study.getStudyIdentifier(), HEALTH_CODE);
    }
    
    @Test
    public void accountFailureConsistent() {
        when(accountDao.getAccount(any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
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
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(false);
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawFromStudyNotificationEmailVerifiedNull() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(null);
        consentService.withdrawFromStudy(study, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawAllWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        study.setConsentNotificationEmailVerified(false);
        consentService.withdrawFromStudy(study, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void withdrawFromOneRequiredConsentSetsAccountToNoSharing() throws Exception {
        setupWithdrawTest(true, false);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
        
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService).deleteAllRegistrations(study.getStudyIdentifier(), HEALTH_CODE);
    }

    @Test
    public void withdrawFromOneOfTwoRequiredConsentsSetsAcountToNoSharing() throws Exception {
        setupWithdrawTest(true, true);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
        
        // You must sign all required consents to sign.
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService).deleteAllRegistrations(study.getStudyIdentifier(), HEALTH_CODE);
    }
    
    @Test
    public void withdrawFromOneOptionalConsentDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, true);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
        
        // Not changed because all the required consents are still signed.
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService, never()).deleteAllRegistrations(any(), any());
    }
    
    @Test
    public void withdrawFromOneOfTwoOptionalConsentsDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, false);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
        
        // Not changed because all the required consents are still signed.
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        assertEquals(new Long(WITHDREW_ON), account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService, never()).deleteAllRegistrations(any(), any());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void withdrawConsentWhenAllConsentsAreWithdrawn() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(true);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(true);
        
        ConsentSignature withdrawnSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withWithdrewOn(WITHDREW_ON).withSignedOn(SIGNED_ON).build();
        
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE, withdrawnSignature));
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(withdrawnSignature));

        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
    }
    
    @Test
    public void consentToResearchNoConsentAdministratorEmail() {
        study.setConsentNotificationEmail(null);
        
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }
    
    @Test
    public void consentToResearchSuppressEmailNotification() {
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(study.getConsentNotificationEmail()));
    }
    
    @Test
    public void consentToResearchNoParticipantEmail() {
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
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
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.ALL_QUALIFIED_RESEARCHERS, true);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void consentToResearchSucceedsWithoutNotification() {
        // In this call, we explicitly override any other settings to suppress notifications
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, false);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void withdrawConsentNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        study.setConsentNotificationEmail(null);
        
        consentService.withdrawConsent(study, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
        
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawAllConsentsNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        study.setConsentNotificationEmail(null);
        
        consentService.withdrawFromStudy(study, PARTICIPANT, WITHDRAWAL, WITHDREW_ON);
        
        verify(sendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void emailConsentAgreementNoConsentAdministratorEmail() {
        study.setConsentNotificationEmail(null);
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, PARTICIPANT);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        assertEquals(1, emailCaptor.getValue().getRecipientEmails().size());
        assertEquals(PARTICIPANT.getEmail(), emailCaptor.getValue().getRecipientEmails().iterator().next());
    }
    
    @Test
    public void emailConsentAgreementDoesNotSuppressEmailNotification() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, PARTICIPANT);
        
        verify(subpopulation, never()).isAutoSendConsentSuppressed();
        
        // Despite explicitly suppressing email, if the user makes this call, we will send the email.
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(1, recipients.size());
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }
    
    @Test(expected = BadRequestException.class)
    public void emailConsentAgreementNoParticipantEmail() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, noEmail);
    }

    @Test
    public void emailConsentAgreementNoRecipients() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        // easiest to test this if we null out the study consent email.
        study.setConsentNotificationEmail(null);
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        try {
            consentService.resendConsentAgreement(study, SUBPOP_GUID, noEmail);
            fail("Should have thrown an exception");
        } catch(BadRequestException e) {
        }
        verify(sendMailService, never()).sendEmail(any());
    }
    
    // Tests of the construction of recipients for email, originally part of special email builder.

    @Test
    public void emailConsentAgreementNotificationEmailVerifiedFalse() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        study.setConsentNotificationEmailVerified(false);

        consentService.resendConsentAgreement(study, SUBPOP_GUID, PARTICIPANT);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate email recipients does not include consent notification email.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(1, recipientList.size());
        assertEquals("email@email.com", recipientList.get(0));
    }
    
    @Test
    public void emailConsentAgreementWithoutStudyRecipientsDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        study.setConsentNotificationEmail(null);
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, PARTICIPANT);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(PARTICIPANT.getEmail(), emailCaptor.getValue().getRecipientEmails().iterator().next());
    }
    
    @Test
    public void consentToResearchNoNotificationEmailSends() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        // For backwards-compatibility, consentNotificationEmailVerified=null means we still send it to the consent
        // notification email.
        study.setConsentNotificationEmailVerified(null);

        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("signedConsent subject", email.getSubject());
        assertEquals("\"Test Study [ConsentServiceTest]\" <bridge-testing+support@sagebase.org>",
                email.getSenderAddress());
        assertEquals(Sets.newHashSet("email@email.com","bridge-testing+consent@sagebase.org"),
                Sets.newHashSet(email.getRecipientAddresses()));
    }

    @Test
    public void consentToResearchNoNotificationEmailVerifiedSends() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        study.setConsentNotificationEmailVerified(false);

        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate email recipients does not include consent notification email.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(1, recipientList.size());
        assertEquals("email@email.com", recipientList.get(0));
    }
    
    @Test
    public void consentToResearchWithNoRecipientsDoesNotSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        study.setConsentNotificationEmail(null);
        
        // The provider reports that there are no addresses to send to, which is correct
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        consentService.consentToResearch(study, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService, never()).sendEmail(emailCaptor.capture());
    }
    
    @Test
    public void consentToResearchWithoutStudyRecipientsDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        study.setConsentNotificationEmail(null);
        
        consentService.consentToResearch(study, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(PARTICIPANT.getEmail(), emailCaptor.getValue().getRecipientEmails().iterator().next());
    }
    
    @Test
    public void consentToResearchWithoutParticipantEmailDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(emailCaptor.capture());
        
        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(study.getConsentNotificationEmail(), emailCaptor.getValue().getRecipientEmails().iterator().next());
    }    
    
    @Test
    public void consentToResearchWithPhoneOK() throws Exception {
        doReturn("asdf.pdf").when(consentService).getSignedConsentUrl();
        
        consentService.consentToResearch(study, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(smsService).sendSmsMessage(eq(ID), smsProviderCaptor.capture());
        
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Helper).writeBytesToS3(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), eq("asdf.pdf"), any(),
                metadataCaptor.capture());
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
        
        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(PHONE_PARTICIPANT.getPhone(), provider.getPhone());
        assertEquals(study, provider.getStudy());
        assertEquals("Transactional", provider.getSmsType());
        assertEquals(SHORT_URL, provider.getTokenMap().get("consentUrl"));
        assertEquals(study.getSignedConsentSmsTemplate(), provider.getTemplate());
    }

    @Test
    public void consentToResearchWithPhoneAutoSuppressed() {
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);
        
        consentService.consentToResearch(study, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);

        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void consentToResearchPrefersEmailToPhone() {
        StudyParticipant phoneAndEmail = new StudyParticipant.Builder().copyOf(PHONE_PARTICIPANT).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, phoneAndEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);
        
        verify(sendMailService).sendEmail(any());
        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void consentToResearchWithPhoneSuppressedByCallFlag() {
        consentService.consentToResearch(study, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING, false);
        
        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void resendConsentAgreementWithPhoneOK() throws Exception {
        doReturn("asdf.pdf").when(consentService).getSignedConsentUrl();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        consentService.resendConsentAgreement(study, SUBPOP_GUID, PHONE_PARTICIPANT);

        verify(smsService).sendSmsMessage(eq(ID), smsProviderCaptor.capture());

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Helper).writeBytesToS3(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), eq("asdf.pdf"), any(),
                metadataCaptor.capture());
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
        
        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(PHONE_PARTICIPANT.getPhone(), provider.getPhone());
        assertEquals(study, provider.getStudy());
        assertEquals("Transactional", provider.getSmsType());
        assertEquals(SHORT_URL, provider.getTokenMap().get("consentUrl"));
        assertEquals(study.getSignedConsentSmsTemplate(), provider.getTemplate());
    }

    @Test
    public void resendConsentAgreementWithPhonePrefersEmailToPhone() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        StudyParticipant phoneAndEmail = new StudyParticipant.Builder().copyOf(PHONE_PARTICIPANT).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();

        consentService.resendConsentAgreement(study, SUBPOP_GUID, phoneAndEmail);
        
        verify(sendMailService).sendEmail(any());
        verify(smsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test(expected = BadRequestException.class)
    public void resendConsentAgreementNoVerifiedChannelThrows() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        
        StudyParticipant noPhoneOrEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withPhone(TestConstants.PHONE).withEmailVerified(null).withPhoneVerified(null).build();
        consentService.resendConsentAgreement(study, SUBPOP_GUID, noPhoneOrEmail);
    }
    
    @Test
    public void getSignedConsentUrl() {
        String url = consentService.getSignedConsentUrl();
        assertTrue(url.endsWith(".pdf"));
        assertEquals(25, url.length());
    }
    
    private void setupWithdrawTest(boolean subpop1Required, boolean subpop2Required) {
        // two consents, withdrawing one does not turn sharing entirely off.
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setHealthCode(PARTICIPANT.getHealthCode());
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(subpop1Required);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(subpop2Required);
        
        doReturn(ImmutableList.of(subpop1, subpop2)).when(subpopService).getSubpopulationsForUser(any());
        
        ConsentSignature secondConsentSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withSignedOn(SIGNED_ON).build();
        
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(secondConsentSignature));
    }
    
    private void setupWithdrawTest() {
        account.setFirstName("Allen");
        account.setLastName("Wrench");
        account.setEmail(EMAIL);
        account.setExternalId("externalId");
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(true);
        account.setExternalId("externalId");
        AccountSubstudy as = AccountSubstudy.create("studyId", "substudyId", ID);
        as.setExternalId("anExternalId");
        account.getAccountSubstudies().add(as);
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
    }

}