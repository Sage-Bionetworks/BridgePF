package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceImplMockTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long UNIX_TIMESTAMP = 1446044925219L;
    
    private ConsentServiceImpl consentService;

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

    private Study study;
    private User user;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() {
        consentService = new ConsentServiceImpl();
        consentService.setAccountDao(accountDao);
        consentService.setOptionsService(optionsService);
        consentService.setSendMailService(sendMailService);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setStudyEnrollmentService(studyEnrollmentService);
        
        study = TestUtils.getValidStudy(ConsentServiceImplMockTest.class);
        user = new User();
        user.setHealthCode("BBB");
        user.setEmail("bbb@bbb.com");
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        
        account = spy(new SimpleAccount()); // mock(Account.class);
        when(accountDao.getAccount(any(Study.class), any(String.class))).thenReturn(account);
        
    }
    
    @Test
    public void activityEventFiredOnConsent() {
        StudyConsent consent = mock(StudyConsent.class);
        
        StudyConsentView view = mock(StudyConsentView.class);
        when(view.getStudyConsent()).thenReturn(consent);
        when(view.getCreatedOn()).thenReturn(UNIX_TIMESTAMP);
        when(studyConsentService.getActiveConsent(SUBPOP_GUID)).thenReturn(view);
        
        UserConsent userConsent = mock(UserConsent.class);
        when(userConsentDao.giveConsent(user.getHealthCode(), SUBPOP_GUID, UNIX_TIMESTAMP, UNIX_TIMESTAMP)).thenReturn(userConsent);
        
        consentService.consentToResearch(study, SUBPOP_GUID, user, consentSignature, SharingScope.NO_SHARING, false);
        
        verify(activityEventService).publishEnrollmentEvent(user.getHealthCode(), userConsent);
    }

    @Test
    public void noActivityEventIfTooYoung() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        study.setMinAgeOfConsent(30); // Test is good until 2044. So there.
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfAlreadyConsented() {
        user.setConsentStatuses(Lists.newArrayList(new ConsentStatus("name", "GUID", true, true, true)));
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfDaoFails() {
        StudyConsent consent = mock(StudyConsent.class);
        when(userConsentDao.giveConsent(user.getHealthCode(), SUBPOP_GUID, consent.getCreatedOn(), UNIX_TIMESTAMP)).thenThrow(new RuntimeException());
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void withdrawConsent() throws Exception {
        List<ConsentSignature> history = account.getConsentSignatureHistory(SUBPOP_GUID);
        history.add(consentSignature);
        consentService.withdrawConsent(study, SUBPOP_GUID, user, new Withdrawal("For reasons."), UNIX_TIMESTAMP);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        
        verify(userConsentDao).withdrawConsent(user.getHealthCode(), SUBPOP_GUID, UNIX_TIMESTAMP);
        verify(accountDao).getAccount(study, user.getEmail());
        verify(accountDao).updateAccount(any(Study.class), captor.capture());
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
        assertFalse(user.doesConsent());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("\"Test Study [ConsentServiceImplMockTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceImplMockTest]", email.getSubject());
        assertEquals("<p>User   &lt;bbb@bbb.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>", 
                    email.getMessageParts().get(0).getContent());
        
        assertFalse(user.doesConsent());
        assertEquals(SharingScope.NO_SHARING, user.getSharingScope());
    }
    
    @Test
    public void stormpathFailureConsistent() {
        when(accountDao.getAccount(any(), any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, user, new Withdrawal("For reasons."), DateTime.now().getMillis());
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
        
        when(accountDao.getAccount(study, user.getEmail())).thenReturn(acct);
        doThrow(new BridgeServiceException("Something bad happend", 500)).when(userConsentDao)
            .withdrawConsent("BBB", SUBPOP_GUID, UNIX_TIMESTAMP);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, user, new Withdrawal("For reasons."), UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
        }
        verify(accountDao).getAccount(any(), any());
        verify(accountDao, times(2)).updateAccount(any(), captor.capture());
        verifyNoMoreInteractions(sendMailService);
        
        Account account = captor.getAllValues().get(1);
        assertEquals(1, account.getConsentSignatureHistory(SUBPOP_GUID).size());
        assertNotNull(account.getActiveConsentSignature(SUBPOP_GUID));
        assertNull(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
    }

    public static class SimpleAccount implements Account {
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private String healthId;
        private StudyIdentifier studyId;
        private Map<String,List<ConsentSignature>> signatures = Maps.newHashMap();
        private Map<String,String> attributes = Maps.newHashMap();
        private Set<Roles> roles = Sets.newHashSet();
        @Override
        public String getUsername() {
            return username;
        }
        @Override
        public void setUsername(String username) {
            this.username = username;
        }
        @Override
        public String getFirstName() {
            return firstName;
        }
        @Override
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        @Override
        public String getLastName() {
            return lastName;
        }
        @Override
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
        @Override
        public String getAttribute(String name) {
            return attributes.get(name);
        }
        @Override
        public void setAttribute(String name, String value) {
            attributes.put(name, value);
        }
        @Override
        public String getEmail() {
            return email;
        }
        @Override
        public void setEmail(String email) {
            this.email = email;
        }
        @Override
        public List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
            signatures.putIfAbsent(subpopGuid.getGuid(), Lists.newArrayList());
            return signatures.get(subpopGuid.getGuid());
        }
        @Override
        public Map<String, List<ConsentSignature>> getAllConsentSignatureHistories() {
            return signatures;
        }
        @Override
        public String getHealthId() {
            return healthId;
        }
        @Override
        public void setHealthId(String healthId) {
            this.healthId = healthId;
        }
        @Override
        public StudyIdentifier getStudyIdentifier() {
            return studyId;
        }
        @Override
        public Set<Roles> getRoles() {
            return roles;
        }
        @Override
        public String getId() {
            return null;
        }
    }
    
}