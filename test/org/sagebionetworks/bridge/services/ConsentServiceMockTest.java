package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("ConstantConditions")
public class ConsentServiceMockTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long SIGNED_ON = 1446044925219L;
    private static final long CONSENT_CREATED_ON = 1446044814108L;
    
    private ConsentService consentService;

    @Mock
    private AccountDao accountDao;
    @Mock
    private ParticipantOptionsService optionsService;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private StudyConsentService studyConsentService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private StudyService studyService;
    @Mock
    private Subpopulation subpopulation;

    private Study study;
    private StudyParticipant participant;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() {
        consentService = new ConsentService();
        consentService.setAccountDao(accountDao);
        consentService.setOptionsService(optionsService);
        consentService.setSendMailService(sendMailService);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setStudyService(studyService);
        consentService.setSubpopulationService(subpopService);
        
        study = TestUtils.getValidStudy(ConsentServiceMockTest.class);
        
        participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withId("user-id")
                .withEmail("bbb@bbb.com").build();
        
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(SIGNED_ON).build();
        
        account = spy(new GenericAccount()); // mock(Account.class);
        when(accountDao.getAccount(any(Study.class), any(String.class))).thenReturn(account);
        
        StudyConsentView studyConsentView = mock(StudyConsentView.class);
        when(studyConsentView.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(studyConsentView);
        when(subpopService.getSubpopulation(study.getStudyIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
        
        when(studyService.getStudy(study.getStudyIdentifier())).thenReturn(study);
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
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(view);

        // Account already has a withdrawn consent, to make sure we're correctly appending consents.
        ConsentSignature withdrawnSig = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(withdrawnSig));

        // Consent signature should have a withdrawOn and a dummy consentCreatedOn just to make sure that we're setting
        // those properly in ConsentService.
        ConsentSignature sig = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withConsentCreatedOn(CONSENT_CREATED_ON + 20000).withWithdrewOn(12345L).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);

        // verify consents were set on account properly
        ArgumentCaptor<Account> updatedAccountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountDao).updateAccount(updatedAccountCaptor.capture());

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
        verify(activityEventService).publishEnrollmentEvent(participant.getHealthCode(), updatedConsentList.get(1));
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
        account.setEmail("bbb@bbb.com");
        
        Map<String,String> optionsMap = Maps.newHashMap();
        optionsMap.put(EXTERNAL_IDENTIFIER.name(), participant.getExternalId());
        
        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(study, participant.getId());
        doReturn(new ParticipantOptionsLookup(optionsMap)).when(optionsService).getOptions(participant.getHealthCode());
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(study.getStudyIdentifier()).build();

        // Add two consents to the account, one withdrawn, one active. This tests to make sure we're not accidentally
        // dropping withdrawn consents from the history.
        ConsentSignature withdrawnConsent = new ConsentSignature.Builder().withConsentSignature(consentSignature)
                .withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000).build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(withdrawnConsent, consentSignature));

        // Execute and validate.
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, new Withdrawal("For reasons."),
                SIGNED_ON + 10000);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        
        verify(accountDao).getAccount(study, participant.getId());
        verify(accountDao).updateAccount(captor.capture());
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
        assertEquals("<p>User   &lt;bbb@bbb.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>", 
                    email.getMessageParts().get(0).getContent());
    }
    
    @Test
    public void withdrawConsentWithAccount() throws Exception {
        Map<String,String> optionsMap = Maps.newHashMap();
        optionsMap.put(ParticipantOption.EXTERNAL_IDENTIFIER.name(), participant.getExternalId());

        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(study, participant.getId());
        doReturn(new ParticipantOptionsLookup(optionsMap)).when(optionsService).getOptions(participant.getHealthCode());
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(study.getStudyIdentifier()).build();
        
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(consentSignature));
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, new Withdrawal("For reasons."), SIGNED_ON);
        
        verify(accountDao).updateAccount(account);
        verify(sendMailService).sendEmail(any(MimeTypeEmailProvider.class));
        
        // Contents of call are tested in prior test where participant is used
    }
    
    @Test
    public void accountFailureConsistent() {
        when(accountDao.getAccount(any(), any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(study.getStudyIdentifier()).build();
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, participant, context, new Withdrawal("For reasons."),
                    DateTime.now().getMillis());
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
            // expected exception
        }
        verifyNoMoreInteractions(sendMailService);
    }

}