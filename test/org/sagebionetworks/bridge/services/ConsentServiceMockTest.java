package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserSession;
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

import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceMockTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long UNIX_TIMESTAMP = 1446044925219L;
    
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
    private UserConsentDao userConsentDao;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private StudyEnrollmentService studyEnrollmentService;
    @Mock
    private SubpopulationService subpopService;

    private Study study;
    private UserSession session;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() {
        consentService = new ConsentService();
        consentService.setAccountDao(accountDao);
        consentService.setOptionsService(optionsService);
        consentService.setSendMailService(sendMailService);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setStudyEnrollmentService(studyEnrollmentService);
        consentService.setSubpopulationService(subpopService);
        
        study = TestUtils.getValidStudy(ConsentServiceMockTest.class);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withEmail("bbb@bbb.com").build();
        session = new UserSession(participant);
        
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        
        session.setConsentStatuses(TestUtils.toMap(
                new ConsentStatus.Builder().withName("Name").withGuid(SUBPOP_GUID).withConsented(false).withRequired(true).build()));
        
        account = spy(new SimpleAccount()); // mock(Account.class);
        when(accountDao.getAccount(any(Study.class), any(String.class))).thenReturn(account);
        
        StudyConsentView studyConsentView = mock(StudyConsentView.class);
        when(studyConsentView.getCreatedOn()).thenReturn(1000L);
        when(studyConsentService.getActiveConsent(SUBPOP_GUID)).thenReturn(studyConsentView);
    }
    
    @Test
    public void userCannotGetConsentForSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        when(subpopService.getSubpopulation(study, badGuid)).thenThrow(new EntityNotFoundException(Subpopulation.class));
        try {
            consentService.getConsentSignature(study, SubpopulationGuid.create("not-correct"), session);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void userCannotConsentToSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        try {
            consentService.consentToResearch(study, badGuid, session, consentSignature, SharingScope.NO_SHARING, false);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void activityEventFiredOnConsent() {
        StudyConsent consent = mock(StudyConsent.class);
        
        StudyConsentView view = mock(StudyConsentView.class);
        when(view.getStudyConsent()).thenReturn(consent);
        when(view.getCreatedOn()).thenReturn(UNIX_TIMESTAMP);
        when(studyConsentService.getActiveConsent(SUBPOP_GUID)).thenReturn(view);
        
        UserConsent userConsent = mock(UserConsent.class);
        when(userConsentDao.giveConsent(session.getStudyParticipant().getHealthCode(), SUBPOP_GUID, UNIX_TIMESTAMP, UNIX_TIMESTAMP)).thenReturn(userConsent);
        
        consentService.consentToResearch(study, SUBPOP_GUID, session, consentSignature, SharingScope.NO_SHARING, false);
        
        verify(activityEventService).publishEnrollmentEvent(session.getStudyParticipant().getHealthCode(), userConsent);
    }

    @Test
    public void noActivityEventIfTooYoung() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        study.setMinAgeOfConsent(30); // Test is good until 2044. So there.
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, session, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfAlreadyConsented() {
        ConsentStatus status = new ConsentStatus.Builder().withName("name").withGuid(SUBPOP_GUID)
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true).build();
        session.setConsentStatuses(TestUtils.toMap(status));
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, session, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfDaoFails() {
        StudyConsent consent = mock(StudyConsent.class);
        when(userConsentDao.giveConsent(session.getStudyParticipant().getHealthCode(), SUBPOP_GUID, consent.getCreatedOn(), UNIX_TIMESTAMP)).thenThrow(new RuntimeException());
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, session, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void withdrawConsent() throws Exception {
        doReturn(new ParticipantOptionsLookup(ImmutableMap.of())).when(optionsService).getOptions(session.getStudyParticipant().getHealthCode());
        
        List<ConsentSignature> history = account.getConsentSignatureHistory(SUBPOP_GUID);
        history.add(consentSignature);
        consentService.withdrawConsent(study, SUBPOP_GUID, session, new Withdrawal("For reasons."), UNIX_TIMESTAMP);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        
        verify(userConsentDao).withdrawConsent(session.getStudyParticipant().getHealthCode(), SUBPOP_GUID, UNIX_TIMESTAMP);
        verify(accountDao).getAccount(study, session.getStudyParticipant().getId());
        verify(accountDao).updateAccount(captor.capture());
        // It happens twice because we do it the first time to set up the test properly
        //verify(account, times(2)).getConsentSignatures(setterCaptor.capture());
        verify(sendMailService).sendEmail(emailCaptor.capture());
        verifyNoMoreInteractions(userConsentDao);
        verifyNoMoreInteractions(accountDao);
        
        Account account = captor.getValue();
        // Signature is there but has been marked withdrawn
        assertNull(account.getActiveConsentSignature(SUBPOP_GUID));
        assertNotNull(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertEquals(1, account.getConsentSignatureHistory(SUBPOP_GUID).size());
        assertFalse(session.doesConsent());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("\"Test Study [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceMockTest]", email.getSubject());
        assertEquals("<p>User   &lt;bbb@bbb.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>", 
                    email.getMessageParts().get(0).getContent());
        
        assertFalse(session.doesConsent());
        assertEquals(SharingScope.NO_SHARING, session.getStudyParticipant().getSharingScope());
    }
    
    @Test
    public void stormpathFailureConsistent() {
        when(accountDao.getAccount(any(), any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, session, new Withdrawal("For reasons."), DateTime.now().getMillis());
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
        }
        verifyNoMoreInteractions(userConsentDao);
        verifyNoMoreInteractions(sendMailService);
    }

    @Test
    public void dynamoDbFailureConsistent() {
        SimpleAccount acct = new SimpleAccount();
        List<ConsentSignature> signatures =  acct.getConsentSignatureHistory(SUBPOP_GUID); 
        signatures.add(new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1969-04-05").build());
        
        when(accountDao.getAccount(study, session.getStudyParticipant().getId())).thenReturn(acct);
        doThrow(new BridgeServiceException("Something bad happend", 500)).when(userConsentDao)
            .withdrawConsent("BBB", SUBPOP_GUID, UNIX_TIMESTAMP);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, session, new Withdrawal("For reasons."), UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
        }
        verify(accountDao).getAccount(any(), any());
        verify(accountDao, times(2)).updateAccount(captor.capture());
        verifyNoMoreInteractions(sendMailService);
        
        Account account = captor.getAllValues().get(1);
        assertEquals(1, account.getConsentSignatureHistory(SUBPOP_GUID).size());
        assertNotNull(account.getActiveConsentSignature(SUBPOP_GUID));
        assertNull(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
    }
    
}