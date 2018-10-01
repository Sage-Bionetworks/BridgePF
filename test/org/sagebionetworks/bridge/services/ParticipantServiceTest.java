package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {
    private static final ClientInfo CLIENT_INFO = new ClientInfo.Builder().withAppName("unit test")
            .withAppVersion(4).build();
    private static final RequestInfo REQUEST_INFO = new RequestInfo.Builder().withClientInfo(CLIENT_INFO)
            .withLanguages(TestConstants.LANGUAGES).withUserDataGroups(TestConstants.USER_DATA_GROUPS).build();
    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2");
    private static final long CONSENT_PUBLICATION_DATE = DateTime.now().getMillis();
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Study STUDY = Study.create();
    static {
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        STUDY.setHealthCodeExportEnabled(true);
        STUDY.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        STUDY.setDataGroups(STUDY_DATA_GROUPS);
        STUDY.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        STUDY.getUserProfileAttributes().add("can_be_recontacted");
    }
    private static final String EXTERNAL_ID = "externalId";
    private static final String HEALTH_CODE = "healthCode";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String PASSWORD = "P@ssword1";
    private static final String ACTIVITY_GUID = "activityGuid";
    private static final String PAGED_BY = "100";
    private static final int PAGE_SIZE = 50;
    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(RESEARCHER);
    private static final Set<Roles> USER_ROLES = Sets.newHashSet(DEVELOPER);
    private static final LinkedHashSet<String> USER_LANGUAGES = (LinkedHashSet<String>)BridgeUtils.commaListToOrderedSet("de,fr");
    private static final String EMAIL = "email@email.com";
    private static final String ID = "ASDF";
    private static final DateTimeZone USER_TIME_ZONE = DateTimeZone.forOffsetHours(-3);
    private static final Map<String,String> ATTRS = new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(STUDY.getIdentifier());
    private static final SubpopulationGuid SUBPOP_GUID_1 = SubpopulationGuid.create("guid1");
    private static final SubpopulationGuid SUBPOP_GUID_2 = SubpopulationGuid.create("guid2");
    private static final AccountId ACCOUNT_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, ID);
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder()
            .withFirstName(FIRST_NAME)
            .withLastName(LAST_NAME)
            .withEmail(EMAIL)
            .withPhone(PHONE)
            .withId(ID)
            .withPassword(PASSWORD)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
            .withNotifyByEmail(true)
            .withRoles(USER_ROLES)
            .withDataGroups(STUDY_DATA_GROUPS)
            .withAttributes(ATTRS)
            .withLanguages(USER_LANGUAGES)
            .withStatus(AccountStatus.DISABLED)
            .withExternalId(EXTERNAL_ID)
            .withTimeZone(USER_TIME_ZONE)
            .withClientData(TestUtils.getClientData()).build();
    
    private static final DateTime START_DATE = DateTime.now();
    private static final DateTime END_DATE = START_DATE.plusDays(1);
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
    
    private ParticipantService participantService;
    
    @Mock
    private AccountDao accountDao;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private Subpopulation subpopulation;
    
    @Mock
    private NotificationsService notificationsService;
    
    @Mock
    private ScheduledActivityService scheduledActivityService;
    
    @Mock
    private PagedResourceList<AccountSummary> accountSummaries;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Mock
    private AccountWorkflowService accountWorkflowService;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @Captor
    ArgumentCaptor<Set<Roles>> rolesCaptor;

    @Captor
    ArgumentCaptor<UserSession> sessionCaptor;
    
    @Captor
    ArgumentCaptor<Study> studyCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Captor
    ArgumentCaptor<SmsMessageProvider> providerCaptor;
    
    private Account account;
    
    @Before
    public void before() {
        STUDY.setExternalIdValidationEnabled(false);
        STUDY.setExternalIdRequiredOnSignup(false);
        STUDY.setEmailVerificationEnabled(false);
        STUDY.setAccountLimit(0);
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
        participantService.setSubpopulationService(subpopService);
        participantService.setUserConsent(consentService);
        participantService.setCacheProvider(cacheProvider);
        participantService.setExternalIdService(externalIdService);
        participantService.setScheduledActivityDao(activityDao);
        participantService.setUploadService(uploadService);
        participantService.setNotificationsService(notificationsService);
        participantService.setScheduledActivityService(scheduledActivityService);
        participantService.setAccountWorkflowService(accountWorkflowService);
        
        account = Account.create();
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null);
    }
    
    private void mockHealthCodeAndAccountRetrieval(String email, Phone phone) {
        TestUtils.mockEditAccount(accountDao, account);
        ((GenericAccount)account).setId(ID);
        ((GenericAccount)account).setHealthCode(HEALTH_CODE);
        account.setEmail(email);
        account.setPhone(phone);
        account.setExternalId(EXTERNAL_ID);
        when(accountDao.constructAccount(any(), any(), any(), any(), any())).thenReturn(account);
        when(accountDao.createAccount(same(STUDY), same(account))).thenReturn(ID);
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
    }
    
    @Test
    public void createParticipant() {
        STUDY.setExternalIdValidationEnabled(true);
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(EXT_ID);
        
        IdentifierHolder idHolder = participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);
        assertEquals(ID, idHolder.getIdentifier());
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        
        // suppress email (true) == sendEmail (false)
        verify(externalIdService).getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture());
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, EMAIL);
        
        Account account = accountCaptor.getValue();
        assertEquals(FIRST_NAME, account.getFirstName());
        assertEquals(LAST_NAME, account.getLastName());
        assertEquals("true", account.getAttribute("can_be_recontacted"));
        assertEquals(USER_ROLES, account.getRoles());
        assertEquals(TestUtils.getClientData(), account.getClientData());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        assertEquals(Boolean.TRUE, account.getNotifyByEmail());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getTimeZone());
        assertEquals(Sets.newHashSet("group1","group2"), account.getDataGroups());
        assertEquals(TestUtils.newLinkedHashSet("de","fr"), account.getLanguages());
        
        // don't update cache
        verify(cacheProvider, never()).removeSessionByUserId(ID);
    }
    
    @Test
    public void createParticipantWithExternalIdValidation() {
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(EXT_ID);
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture());
        
        Account account = accountCaptor.getValue();
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void createParticipantWithInvalidParticipant() {
        // It doesn't get more invalid than this...
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
        }
        verifyNoMoreInteractions(accountDao);
        verifyNoMoreInteractions(externalIdService);
    }
    
    @Test
    public void createParticipantEmailDisabledNoVerificationWanted() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailDisabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withPhone(null).build();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, participant, true);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailEnabledNoVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailEnabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();

        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);

        verify(accountWorkflowService).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
    }

    @Test
    public void createParticipantAutoVerificationEmailSuppressed() {
        Study study = makeStudy();
        study.setEmailVerificationEnabled(true);
        study.setAutoVerificationEmailSuppressed(true);
        mockHealthCodeAndAccountRetrieval();

        participantService.createParticipant(study, CALLER_ROLES, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
    }

    @Test
    public void createParticipantPhoneNoEmailVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();

        // Make minimal phone participant.
        StudyParticipant phoneParticipant = new StudyParticipant.Builder().withPhone(PHONE).build();
        participantService.createParticipant(STUDY, CALLER_ROLES, phoneParticipant, false);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertNull(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantPhoneDisabledNoVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
    }
    
    @Test
    public void createParticipantPhoneEnabledVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        STUDY.setEmailVerificationEnabled(true);
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);

        verify(accountWorkflowService).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createParticipantAutoVerificationPhoneSuppressed() {
        Study study = makeStudy();
        study.setAutoVerificationPhoneSuppressed(true);
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        study.setEmailVerificationEnabled(true);
        participantService.createParticipant(study, CALLER_ROLES, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createParticipantEmailNoPhoneVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        // Make minimal email participant.
        StudyParticipant emailParticipant = new StudyParticipant.Builder().withEmail(EMAIL).build();
        participantService.createParticipant(STUDY, CALLER_ROLES, emailParticipant, false);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createParticipantExternalIdNoPasswordIsUnverified() {
        mockHealthCodeAndAccountRetrieval(null, null);
        
        StudyParticipant idParticipant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(STUDY, CALLER_ROLES, idParticipant, false);
        
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
        assertNull(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantExternalIdAndPasswordIsEnabled() {
        mockHealthCodeAndAccountRetrieval(null, null);
        
        StudyParticipant idParticipant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID)
                .withPassword(PASSWORD).build();
        participantService.createParticipant(STUDY, CALLER_ROLES, idParticipant, false);
        
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertNull(account.getPhoneVerified());
        assertNull(account.getEmailVerified());
    }

    @Test
    public void createSmsNotificationRegistration_PhoneNotVerified() {
        // Mock account w/ email but no phone.
        mockHealthCodeAndAccountRetrieval(EMAIL, null);
        account.setPhoneVerified(null);

        // Execute.
        try {
            participantService.createSmsRegistration(STUDY, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user has no verified phone number"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_NoRequestInfo() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        account.setPhoneVerified(true);

        // Mock request info to return null.
        when(cacheProvider.getRequestInfo(ID)).thenReturn(null);

        // Execute.
        try {
            participantService.createSmsRegistration(STUDY, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user has no request info"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_NotConsented() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        account.setPhoneVerified(true);

        // Mock request info.
        when(cacheProvider.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock subpop service.
        when(subpopulation.getGuid()).thenReturn(SUBPOP_GUID);
        when(subpopulation.getGuidString()).thenReturn(SUBPOP_GUID.getGuid());
        when(subpopService.getSubpopulations(TestConstants.TEST_STUDY, false)).thenReturn(
                ImmutableList.of(subpopulation));

        // Mock consent service
        ConsentStatus consentStatus = new ConsentStatus.Builder().withName("My Consent").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(false).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(ImmutableMap.of(SUBPOP_GUID, consentStatus));

        // Execute.
        try {
            participantService.createSmsRegistration(STUDY, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user is not consented"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_Success() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        account.setPhoneVerified(true);

        // Mock request info.
        when(cacheProvider.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock subpop service.
        when(subpopulation.getGuid()).thenReturn(SUBPOP_GUID);
        when(subpopulation.getGuidString()).thenReturn(SUBPOP_GUID.getGuid());
        when(subpopService.getSubpopulations(TestConstants.TEST_STUDY, false)).thenReturn(
                ImmutableList.of(subpopulation));

        // Mock consent service
        ConsentStatus consentStatus = new ConsentStatus.Builder().withName("My Consent").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(true).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(ImmutableMap.of(SUBPOP_GUID, consentStatus));

        // Execute.
        participantService.createSmsRegistration(STUDY, ID);

        // Verify.
        ArgumentCaptor<CriteriaContext> criteriaContextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        ArgumentCaptor<NotificationRegistration> registrationCaptor = ArgumentCaptor.forClass(
                NotificationRegistration.class);
        verify(notificationsService).createRegistration(eq(TestConstants.TEST_STUDY), criteriaContextCaptor.capture(),
                registrationCaptor.capture());

        CriteriaContext criteriaContext = criteriaContextCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY, criteriaContext.getStudyIdentifier());
        assertEquals(ID, criteriaContext.getUserId());
        assertEquals(HEALTH_CODE, criteriaContext.getHealthCode());
        assertEquals(CLIENT_INFO, criteriaContext.getClientInfo());
        assertEquals(TestConstants.LANGUAGES, criteriaContext.getLanguages());
        assertEquals(TestConstants.USER_DATA_GROUPS, criteriaContext.getUserDataGroups());

        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(HEALTH_CODE, registration.getHealthCode());
        assertEquals(NotificationProtocol.SMS, registration.getProtocol());
        assertEquals(PHONE.getNumber(), registration.getEndpoint());
    }

    @Test
    public void getPagedAccountSummaries() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(1100)
                .withPageSize(50)
                .withEmailFilter("foo")
                .withPhoneFilter("bar")
                .withStartTime(START_DATE)
                .withEndTime(END_DATE).build();
        
        participantService.getPagedAccountSummaries(STUDY, search);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, search); 
    }
    
    @Test(expected = NullPointerException.class)
    public void getPagedAccountSummariesWithBadStudy() {
        participantService.getPagedAccountSummaries(null, AccountSummarySearch.EMPTY_SEARCH);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithNegativeOffsetBy() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(-1).build();
        participantService.getPagedAccountSummaries(STUDY, search);
    }

    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithNegativePageSize() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withPageSize(-100).build();
        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithBadDateRange() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withStartTime(END_DATE).withEndTime(START_DATE).build();
        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test
    public void getPagedAccountSummariesWithoutEmailOrPhoneFilterOK() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(1100).withPageSize(50).build();
        
        participantService.getPagedAccountSummaries(STUDY, search);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, search); 
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithTooLargePageSize() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withPageSize(251).build();
        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithInvalidAllOfGroup() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withAllOfGroups(Sets.newHashSet("not_real_group")).build();

        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithInvalidNoneOfGroup() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("not_real_group")).build();

        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void getPagedAccountSummariesWithConflictingGroups() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("group1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();
        participantService.getPagedAccountSummaries(STUDY, search);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        participantService.getParticipant(STUDY, ID, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantAccountIdDoesNotExist() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        AccountId wrongAccountId = AccountId.forExternalId(STUDY.getIdentifier(), "some-junk");
        
        participantService.getParticipant(STUDY, wrongAccountId, false);
    }
    
    @Test
    public void getStudyParticipant() {
        // A lot of mocks have to be set up first, this call aggregates almost everything we know about the user
        DateTime createdOn = DateTime.now();
        ((GenericAccount)account).setHealthCode(HEALTH_CODE);
        ((GenericAccount)account).setStudyId(STUDY.getStudyIdentifier());
        ((GenericAccount)account).setId(ID);
        ((GenericAccount)account).setCreatedOn(createdOn);
        account.setFirstName(FIRST_NAME);
        account.setLastName(LAST_NAME);
        account.setEmailVerified(Boolean.TRUE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setStatus(AccountStatus.DISABLED);
        account.setAttribute("attr2", "anAttribute2");
        List<ConsentSignature> sigs1 = Lists.newArrayList(new ConsentSignature.Builder()
                .withName("Name 1").withBirthdate("1980-01-01").build());
        account.setConsentSignatureHistory(SUBPOP_GUID_1, sigs1);
        account.setClientData(TestUtils.getClientData());
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(Boolean.TRUE);
        account.setExternalId(EXTERNAL_ID);
        account.setDataGroups(TestUtils.newLinkedHashSet("group1","group2"));
        account.setLanguages(USER_LANGUAGES);
        account.setTimeZone(USER_TIME_ZONE);
        
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE);
        
        List<Subpopulation> subpopulations = Lists.newArrayList();
        // Two subpopulations for mocking.
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setGuidString("guid1");
        subpop1.setPublishedConsentCreatedOn(CONSENT_PUBLICATION_DATE);
        subpopulations.add(subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setGuidString("guid2");
        subpop2.setPublishedConsentCreatedOn(CONSENT_PUBLICATION_DATE);
        
        subpopulations.add(subpop2);
        when(subpopService.getSubpopulations(STUDY.getStudyIdentifier(), false)).thenReturn(subpopulations);

        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SUBPOP_GUID_1)).thenReturn(subpop1);
        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SUBPOP_GUID_2)).thenReturn(subpop2);

        // Mock CacheProvider to return request info.
        when(cacheProvider.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock ConsentService to return consent statuses for criteria.
        ConsentStatus consentStatus1 = new ConsentStatus.Builder().withName("consent1").withGuid(SUBPOP_GUID_1)
                .withRequired(true).withConsented(true).withSignedMostRecentConsent(true).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(
                ImmutableMap.of(SUBPOP_GUID_1, consentStatus1));
        
        // Get the fully initialized participant object (including histories)
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);

        assertTrue(participant.isConsented());
        assertEquals(FIRST_NAME, participant.getFirstName());
        assertEquals(LAST_NAME, participant.getLastName());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), participant.getDataGroups());
        assertEquals(EXTERNAL_ID, participant.getExternalId());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, participant.getSharingScope());
        assertEquals(HEALTH_CODE, participant.getHealthCode());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals(PHONE.getNationalFormat(), participant.getPhone().getNationalFormat());
        assertEquals(Boolean.TRUE, participant.getEmailVerified());
        assertEquals(Boolean.FALSE, participant.getPhoneVerified());
        assertEquals(ID, participant.getId());
        assertEquals(AccountStatus.DISABLED, participant.getStatus());
        assertEquals(createdOn, participant.getCreatedOn());
        assertEquals(USER_TIME_ZONE, participant.getTimeZone());
        assertEquals(USER_LANGUAGES, participant.getLanguages());
        assertEquals(TestUtils.getClientData(), participant.getClientData());
        
        assertNull(participant.getAttributes().get("attr1"));
        assertEquals("anAttribute2", participant.getAttributes().get("attr2"));
        
        List<UserConsentHistory> retrievedHistory1 = participant.getConsentHistories().get(subpop1.getGuidString());
        assertEquals(1, retrievedHistory1.size());
        
        List<UserConsentHistory> retrievedHistory2 = participant.getConsentHistories().get(subpop2.getGuidString());
        assertTrue(retrievedHistory2.isEmpty());

        // Verify context passed to consent service.
        ArgumentCaptor<CriteriaContext> criteriaContextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        verify(consentService).getConsentStatuses(criteriaContextCaptor.capture(), same(account));

        CriteriaContext criteriaContext = criteriaContextCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY, criteriaContext.getStudyIdentifier());
        assertEquals(ID, criteriaContext.getUserId());
        assertEquals(HEALTH_CODE, criteriaContext.getHealthCode());
        assertEquals(CLIENT_INFO, criteriaContext.getClientInfo());
        assertEquals(TestConstants.LANGUAGES, criteriaContext.getLanguages());
        assertEquals(TestConstants.USER_DATA_GROUPS, criteriaContext.getUserDataGroups());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signOutUserWhoDoesNotExist() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(null);
        participantService.signUserOut(STUDY, ID, true);
    }

    @Test
    public void signOutUser() {
        // Setup
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        ((GenericAccount)account).setId("userId");

        // Execute
        participantService.signUserOut(STUDY, ID, false);

        // Verify
        verify(accountDao).getAccount(ACCOUNT_ID);
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider).removeSessionByUserId("userId");
    }

    @Test
    public void signOutUserDeleteReauthToken() {
        // Setup
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        ((GenericAccount)account).setId("userId");

        // Execute
        participantService.signUserOut(STUDY, ID, true);

        // Verify
        verify(accountDao).getAccount(ACCOUNT_ID);
        verify(accountDao).deleteReauthToken(accountIdCaptor.capture());
        verify(cacheProvider).removeSessionByUserId("userId");

        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountIdCaptor.getValue().getStudyId());
        assertEquals("userId", accountIdCaptor.getValue().getId());
    }

    @Test
    public void updateParticipantWithExternalIdValidationAddingId() {
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, EXTERNAL_ID)).thenReturn(EXT_ID);
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null); // account can be updated because it's null

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withEmail(EMAIL)
                .withId(ID).build();
        
        UserSession oldSession = new UserSession(participant);
        oldSession.setSessionToken("sessionToken");
        oldSession.setInternalSessionToken("internalSessionToken");
        oldSession.setEnvironment(Environment.DEV);
        oldSession.setAuthenticated(true);
        oldSession.setStudyIdentifier(STUDY.getStudyIdentifier());
        doReturn(oldSession).when(cacheProvider).getUserSessionByUserId(ID);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        Account account = accountCaptor.getValue();
        assertEquals(FIRST_NAME, account.getFirstName());
        assertEquals(LAST_NAME, account.getLastName());
        assertEquals("true", account.getAttribute("can_be_recontacted"));
        assertEquals(TestUtils.getClientData(), account.getClientData());
        
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        assertEquals(Boolean.TRUE, account.getNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), account.getDataGroups());
        assertEquals(TestUtils.newLinkedHashSet("de","fr"), account.getLanguages());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getTimeZone());
    }
    
    @Test
    public void updateParticipantWithSameExternalIdDoesntAssignExtId() {
        mockHealthCodeAndAccountRetrieval();
        
        // account and participant have the same ID, so externalIdService is not called
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
    }
    
    @Test
    public void updateParticipantWithNoExternalIdDoesntAssignExtId() {
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);

        // Paticipant has no external ID, so externalIdService is not called
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateValidates() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withId(ID)
                .withDataGroups(Sets.newHashSet("bogusGroup"))
                .build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
    }
    
    @Test
    public void updateThrowsExceptionIfUserMissing() {
        doThrow(new EntityNotFoundException(Account.class)).when(accountDao).getAccount(ACCOUNT_ID);
        try {
            participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
        }
        verify(accountDao, never()).updateAccount(any(), Mockito.anyBoolean());
        verifyNoMoreInteractions(externalIdService);
    }
    
    @Test
    public void userCannotChangeStatus() {
        verifyStatusUpdate(EnumSet.noneOf(Roles.class), false);
    }
    
    @Test
    public void developerCannotChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(DEVELOPER), false);
    }
    
    @Test
    public void researcherCannotChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(RESEARCHER), false);
    }
    
    @Test
    public void adminCanChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(ADMIN), true);
    }

    @Test
    public void workerCanChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(WORKER), true);
    }

    @Test
    public void notSettingStatusDoesntClearStatus() {
        mockHealthCodeAndAccountRetrieval();
        account.setStatus(AccountStatus.ENABLED);

        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(null).build();

        participantService.updateParticipant(STUDY, EnumSet.of(ADMIN), participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        Account account = accountCaptor.getValue();
        assertEquals(AccountStatus.ENABLED, account.getStatus());
    }

    @Test
    public void userCannotCreateAnyRoles() {
        verifyRoleCreate(Sets.newHashSet(), null);
    }
    
    @Test
    public void developerCanCreateDeveloperRole() {
        verifyRoleCreate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER));
    }
    
    @Test
    public void researcherCanCreateDeveloperOrResearcherRole() {
        verifyRoleCreate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void adminCanCreateAllRoles() {
        verifyRoleCreate(Sets.newHashSet(ADMIN), Sets.newHashSet(DEVELOPER, RESEARCHER, ADMIN, WORKER));
    }
    
    @Test
    public void userCannotUpdateAnyRoles() {
        verifyRoleUpdate(Sets.newHashSet(), null);
    }
    
    @Test
    public void developerCanUpdateDeveloperRole() {
        verifyRoleUpdate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER));
    }
    
    @Test
    public void researcherCanUpdateDeveloperOrResearcherRole() {
        verifyRoleUpdate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void adminCanUpdateAllRoles() {
        verifyRoleUpdate(Sets.newHashSet(ADMIN), Sets.newHashSet(DEVELOPER, RESEARCHER, ADMIN, WORKER));
    }
    
    @Test
    public void getParticipantWithoutHistories() {
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(STUDY.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier(), false);
        
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, false);

        assertTrue(participant.getConsentHistories().keySet().isEmpty());
        assertNull(participant.isConsented());
    }

    @Test
    public void getParticipantByAccountId() {
        AccountId accountId = AccountId.forEmail(STUDY.getIdentifier(), "email@email.com");
        when(accountDao.getAccount(accountId)).thenReturn(account);
        
        StudyParticipant participant = participantService.getParticipant(STUDY, accountId, true);
        
        assertNotNull(participant);
        verify(accountDao).getAccount(accountId);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantByAccountIdThrowsException() {
        AccountId accountId = AccountId.forEmail(STUDY.getIdentifier(), "email@email.com");
        
        participantService.getParticipant(STUDY, accountId, true);
    }
    
    @Test
    public void getParticipantWithHistories() {
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(STUDY.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier(), false);
        
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);

        assertEquals(1, participant.getConsentHistories().keySet().size());
    }

    @Test
    public void getParticipantIsConsentedWithoutRequestInfo() {
        // Set up mocks.
        mockHealthCodeAndAccountRetrieval();

        doReturn(STUDY.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier(), false);

        // Execute and validate
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);
        assertNull(participant.isConsented());
    }

    @Test
    public void getParticipantIsConsentedFalse() {
        // Set up mocks.
        mockHealthCodeAndAccountRetrieval();

        doReturn(STUDY.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier(), false);

        when(cacheProvider.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        ConsentStatus consentStatus1 = new ConsentStatus.Builder().withName("consent1").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(false).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(
                ImmutableMap.of(SUBPOP_GUID_1, consentStatus1));

        // Execute and validate
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);
        assertFalse(participant.isConsented());
    }

    // Now, verify that roles cannot *remove* roles they don't have permissions to remove
    
    @Test
    public void developerCannotDowngradeAdmin() {
        account.setRoles(Sets.newHashSet(ADMIN));
        
        // developer can add the developer role, but they cannot remove the admin role
        verifyRoleUpdate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(ADMIN, DEVELOPER));
    }
    
    @Test
    public void developerCannotDowngradeResearcher() {
        account.setRoles(Sets.newHashSet(RESEARCHER));
        
        // developer can add the developer role, but they cannot remove the researcher role
        verifyRoleUpdate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void researcherCanDowngradeResearcher() {
        account.setRoles(Sets.newHashSet(RESEARCHER));
        
        // researcher can change a researcher to a developer
        verifyRoleUpdate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER));
    }
    
    @Test
    public void adminCanChangeDeveloperToResearcher() {
        account.setRoles(Sets.newHashSet(DEVELOPER));
        
        // admin can convert a developer to a researcher
        verifyRoleUpdate(Sets.newHashSet(ADMIN), Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER));
    }
    
    @Test
    public void adminCanChangeResearcherToAdmin() {
        account.setRoles(Sets.newHashSet(RESEARCHER));
        
        // admin can convert a researcher to an admin
        verifyRoleUpdate(Sets.newHashSet(ADMIN), Sets.newHashSet(ADMIN), Sets.newHashSet(ADMIN));
    }
    
    @Test
    public void researcherCanUpgradeDeveloperRole() {
        account.setRoles(Sets.newHashSet(DEVELOPER));
        
        // researcher can convert a developer to a researcher
        verifyRoleUpdate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER));
    }
    
    @Test
    public void getStudyParticipantWithAccount() throws Exception {
        mockHealthCodeAndAccountRetrieval();
        account.setClientData(TestUtils.getClientData());
        
        StudyParticipant participant = participantService.getParticipant(STUDY, account, false);
        
        // The most important thing here is that participant includes health code
        assertEquals(HEALTH_CODE, participant.getHealthCode());
        // Other fields exist too, but getParticipant() is tested in its entirety earlier in this test.
        assertEquals(EMAIL, participant.getEmail());
        assertEquals(ID, participant.getId());
        assertEquals(TestUtils.getClientData(), participant.getClientData());
    }

    // Contrived test case for a case that never happens, but somehow does.
    // See https://sagebionetworks.jira.com/browse/BRIDGE-1463
    @Test(expected = EntityNotFoundException.class)
    public void getStudyParticipantWithoutAccountThrows404() {
        participantService.getParticipant(STUDY, (Account) null, false);
    }

    @Test
    public void requestResetPassword() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.requestResetPassword(STUDY, ID);
        
        verify(accountWorkflowService).requestResetPassword(STUDY, true, ACCOUNT_ID);
    }
    
    @Test
    public void requestResetPasswordNoAccountIsSilent() {
        participantService.requestResetPassword(STUDY, ID);
        
        verifyNoMoreInteractions(accountDao);
    }
    
    @Test
    public void canGetActivityHistoryV2WithAllValues() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(STUDY, ID, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY, PAGE_SIZE);

        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY,
                PAGE_SIZE);
    }
    
    @Test
    public void canGetActivityHistoryV2WithDefaults() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(STUDY, ID, ACTIVITY_GUID, null, null, null, PAGE_SIZE);

        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, null, null, PAGE_SIZE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityHistoryV2NoUserThrowsCorrectException() {
        participantService.getActivityHistory(STUDY, ID, ACTIVITY_GUID, null, null, null, PAGE_SIZE);
    }
    
    @Test
    public void deleteActivities() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.deleteActivities(STUDY, ID);
        
        verify(activityDao).deleteActivitiesForUser(HEALTH_CODE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteActivitiesNoUserThrowsCorrectException() {
        participantService.deleteActivities(STUDY, ID);
    }
    
    @Test
    public void resendEmailVerification() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.resendVerification(STUDY, ChannelType.EMAIL, ID);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.EMAIL), accountIdCaptor.capture());
        
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(STUDY.getIdentifier(), accountId.getStudyId());
        assertEquals(EMAIL, accountId.getEmail());
    }
    
    @Test
    public void resendPhoneVerification() {
        mockHealthCodeAndAccountRetrieval(null, TestConstants.PHONE);
        
        participantService.resendVerification(STUDY, ChannelType.PHONE, ID);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.PHONE), accountIdCaptor.capture());
        
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(STUDY.getIdentifier(), accountId.getStudyId());
        assertEquals(PHONE, accountId.getPhone());
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void resendVerificationUnsupportedOperationException() {
        mockHealthCodeAndAccountRetrieval();
        
        // Use null so we don't have to create a dummy unsupported channel type
        participantService.resendVerification(STUDY, null, ID);
    }

    @Test
    public void resendConsentAgreement() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.resendConsentAgreement(STUDY, SUBPOP_GUID, ID);
        
        verify(consentService).resendConsentAgreement(eq(STUDY), eq(SUBPOP_GUID), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(ID, participant.getId());
    }
    
    @Test
    public void withdrawAllConsents() {
        mockHealthCodeAndAccountRetrieval();
        
        Withdrawal withdrawal = new Withdrawal("Reasons");
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawFromStudy(STUDY, ID, withdrawal, withdrewOn);
        
        verify(consentService).withdrawFromStudy(eq(STUDY), participantCaptor.capture(),
            eq(withdrawal), eq(withdrewOn));
        assertEquals(ID, participantCaptor.getValue().getId());
    }
    
    @Test
    public void withdrawConsent() {
        mockHealthCodeAndAccountRetrieval();
        
        Withdrawal withdrawal = new Withdrawal("Reasons");
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawConsent(STUDY, ID, SUBPOP_GUID, withdrawal, withdrewOn);
        
        verify(consentService).withdrawConsent(eq(STUDY), eq(SUBPOP_GUID), participantCaptor.capture(),
                contextCaptor.capture(), eq(withdrawal), eq(withdrewOn));
        assertEquals(ID, participantCaptor.getValue().getId());
        assertEquals(ID, contextCaptor.getValue().getUserId());
    }
    
    @Test
    public void getUploads() {
        mockHealthCodeAndAccountRetrieval();
        DateTime startTime = DateTime.parse("2015-11-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2015-11-01T23:59:59.999Z");
        
        participantService.getUploads(STUDY, ID, startTime, endTime, 10, "ABC");
        
        verify(uploadService).getUploads(HEALTH_CODE, startTime, endTime, 10, "ABC");
    }
    
    @Test
    public void getUploadsWithoutDates() {
        // Just verify this throws no exceptions
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getUploads(STUDY, ID, null, null, 10, null);
        
        verify(uploadService).getUploads(HEALTH_CODE, null, null, 10, null);
    }
    
    @Test
    public void listNotificationRegistrations() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.listRegistrations(STUDY, ID);
        
        verify(notificationsService).listRegistrations(HEALTH_CODE);
    }
    
    @Test
    public void sendNotification() {
        mockHealthCodeAndAccountRetrieval();
        
        Set<String> erroredNotifications = ImmutableSet.of("ABC");
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        when(notificationsService.sendNotificationToUser(any(), any(), any())).thenReturn(erroredNotifications);
        
        Set<String> returnedErrors = participantService.sendNotification(STUDY, ID, message);
        assertEquals(erroredNotifications, returnedErrors);
        
        verify(notificationsService).sendNotificationToUser(STUDY.getStudyIdentifier(), HEALTH_CODE, message);
    }

    // Creating an account and supplying an externalId
    
    @Test
    public void callsExternalIdService() {
        STUDY.setExternalIdValidationEnabled(true);
        STUDY.setExternalIdRequiredOnSignup(true);
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(EXT_ID);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        // Validated and required, use reservation service and don't set as option
        verify(externalIdService).assignExternalId(studyCaptor.capture(), eq(EXTERNAL_ID), eq(HEALTH_CODE));
        assertTrue(studyCaptor.getAllValues().get(0).isExternalIdValidationEnabled());
    }

    @Test
    public void limitNotExceededException() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(9);
        when(accountDao.getPagedAccountSummaries(STUDY, AccountSummarySearch.EMPTY_SEARCH))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(STUDY,  CALLER_ROLES, PARTICIPANT, false);
    }
    
    @Test
    public void throwLimitExceededExactlyException() {
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(10);
        when(accountDao.getPagedAccountSummaries(STUDY, AccountSummarySearch.EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(LimitExceededException e) {
            assertEquals("While study is in evaluation mode, it may not exceed 10 accounts.", e.getMessage());
        }
    }
    
    @Test(expected = LimitExceededException.class)
    public void throwLimitExceededException() {
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(13);
        when(accountDao.getPagedAccountSummaries(STUDY, AccountSummarySearch.EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
    }

    @Test(expected = InvalidEntityException.class)
    public void createRequiredExternalIdValidated() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withRoles(Sets.newHashSet())
                .withExternalId(null).build();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, participant, false);
    }

    public void createRequiredExternalIdWithRolesOK() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withExternalId(null).build();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, participant, false);
    }
    
    @Test
    public void createManagedExternalIdOK() {
        mockHealthCodeAndAccountRetrieval();
        
        STUDY.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(EXT_ID);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(externalIdService).getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void badManagedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(null);
        
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(accountDao, never()).createAccount(any(), any());
            verify(externalIdService, never()).assignExternalId(any(), any(), any());    
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }
    
    @Test
    public void usedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        identifier.setHealthCode("AAA");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(identifier);
        
        when(accountDao.createAccount(any(), any()))
                .thenThrow(new EntityAlreadyExistsException(Account.class, (String) null, (Map<String, Object>) null));
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
            verify(externalIdService, never()).assignExternalId(any(), any(), any());    
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateMissingManagedExternalIdFails() {
        // In this case the ID is not in the external IDs table, so it fails validation.
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
    }
    
    @Test
    public void updateAddsManagedExternalId() {
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(identifier);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void updatingBlankExternalIdFails() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT)
                .withExternalId("").build();
        try {
            participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            
        }
        verify(externalIdService, never()).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void updateChangesManagedExternalId() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId")).thenReturn(identifier);
        
        // This record has a different external ID than the mocked account
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals("newExternalId", account.getExternalId());
        verify(externalIdService).unassignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(externalIdService).assignExternalId(STUDY, "newExternalId", HEALTH_CODE);
    }
    
    @Test
    public void sameManagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(identifier);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void updateRemovesManagedExternalId() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(identifier);
        
        // This record has a different external ID than the mocked account
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        try {
            participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("externalId cannot be deleted", e.getErrors().get("externalId").get(0));
        }
        verify(externalIdService, never()).unassignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(externalIdService, never()).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void createUnmanagedExternalIdWillAssign() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(null);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }
    
    @Test
    public void updateAddsUnmanagedExternalId() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        account.setExternalId(null);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).unassignExternalId(eq(STUDY), any(), eq(HEALTH_CODE));
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
    }

    @Test
    public void sameUnmanagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
    }
    
    @Test
    public void updateChangesUnmanagedExternalId() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withId(ID).withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals("newExternalId", account.getExternalId());
        verify(externalIdService).unassignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(externalIdService).assignExternalId(STUDY, "newExternalId", HEALTH_CODE);
    }
    
    @Test
    public void updateRemovesExternalId() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        account.setExternalId(EXTERNAL_ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertNull(account.getExternalId());
        verify(externalIdService).unassignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(externalIdService).assignExternalId(STUDY, null, HEALTH_CODE);
    }
    
    @Test
    public void sendSmsMessage() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        SmsTemplate template = new SmsTemplate("This is a test ${studyShortName}"); 
        
        participantService.sendSmsMessage(STUDY, ID, template);

        verify(notificationsService).sendSmsMessage(providerCaptor.capture());
        
        SmsMessageProvider provider = providerCaptor.getValue();
        assertEquals(TestConstants.PHONE, provider.getPhone());
        assertEquals("This is a test Bridge", provider.getSmsRequest().getMessage());
        assertEquals("Promotional", provider.getSmsType());
    }
    
    @Test(expected = BadRequestException.class)
    public void sendSmsMessageThrowsIfNoPhone() { 
        when(accountDao.getAccount(any())).thenReturn(account);
        
        SmsTemplate template = new SmsTemplate("This is a test ${studyShortName}"); 
        
        participantService.sendSmsMessage(STUDY, ID, template);
    }
    
    @Test(expected = BadRequestException.class)
    public void sendSmsMessageThrowsIfPhoneUnverified() { 
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(false);
        
        SmsTemplate template = new SmsTemplate("This is a test ${studyShortName}"); 
        
        participantService.sendSmsMessage(STUDY, ID, template);
    }
    
    @Test(expected = BadRequestException.class)
    public void sendSmsMessageThrowsIfBlankMessage() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        SmsTemplate template = new SmsTemplate("    "); 
        
        participantService.sendSmsMessage(STUDY, ID, template);
    }
    
    @Test
    public void updateAddsEmail() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setEmail(null);
        account.setEmailVerified(null);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertEquals(EMAIL, accountCaptor.getValue().getEmail());
        assertFalse(accountCaptor.getValue().getEmailVerified());
        
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, EMAIL);
    }
    
    @Test
    public void updateChangesEmail() { 
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setEmail("someotheremail@email.com");
        account.setEmailVerified(true);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertEquals(EMAIL, accountCaptor.getValue().getEmail());
        assertFalse(accountCaptor.getValue().getEmailVerified());
        
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, EMAIL);
    }
    
    @Test
    public void updateRemovesEmail() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertNull(accountCaptor.getValue().getEmail());
        assertFalse(accountCaptor.getValue().getEmailVerified());
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateAddsPhone() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setPhone(null);
        account.setEmailVerified(null);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertEquals(PHONE, accountCaptor.getValue().getPhone());
        assertFalse(accountCaptor.getValue().getPhoneVerified());
        
        verify(accountWorkflowService).sendPhoneVerificationToken(STUDY, ID, PHONE);
    }
    
    @Test
    public void updateChangesPhone() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setPhone(TestConstants.OTHER_PHONE);
        account.setPhoneVerified(true);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertEquals(PHONE, accountCaptor.getValue().getPhone());
        assertFalse(accountCaptor.getValue().getPhoneVerified());
        
        verify(accountWorkflowService).sendPhoneVerificationToken(STUDY, ID, PHONE);
    }
    
    @Test
    public void updateRemovesPhone() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setId(ID);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withPhone(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        
        assertNull(accountCaptor.getValue().getPhone());
        assertFalse(accountCaptor.getValue().getPhoneVerified());
        
        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());        
    }
    
    @Test
    public void updateAdminCanChangeUserStatus() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setStatus(AccountStatus.ENABLED); // participant is disabled
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(Roles.ADMIN), PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        assertEquals(AccountStatus.DISABLED, accountCaptor.getValue().getStatus());
    }
    
    @Test
    public void updateWorkerCanChangeUserStatus() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setStatus(AccountStatus.ENABLED); // participant is disabled
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(Roles.WORKER), PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        assertEquals(AccountStatus.DISABLED, accountCaptor.getValue().getStatus());
    }
    
    @Test
    public void updateResearchCannotChangeUserStatus() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setStatus(AccountStatus.ENABLED); // participant is disabled
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(Roles.RESEARCHER), PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        assertEquals(AccountStatus.ENABLED, accountCaptor.getValue().getStatus());
    }

    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> roles, boolean canSetStatus) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(AccountStatus.ENABLED).build();
        
        participantService.updateParticipant(STUDY, roles, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        Account account = accountCaptor.getValue();

        if (canSetStatus) {
            assertEquals(AccountStatus.ENABLED, account.getStatus());
        } else {
            assertNull(account.getStatus());
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(STUDY, callerRoles, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        if (rolesThatAreSet != null) {
            assertEquals(rolesThatAreSet, account.getRoles());
        } else {
            assertEquals(Sets.newHashSet(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet, Set<Roles> expected) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(rolesThatAreSet).build();
        participantService.updateParticipant(STUDY, callerRoles, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(true));
        Account account = accountCaptor.getValue();
        
        if (expected != null) {
            assertEquals(expected, account.getRoles());
        } else {
            assertEquals(Sets.newHashSet(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> expected) {
        verifyRoleUpdate(callerRoles, Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER), expected);
    }

    // Makes a study instance, so tests can modify it without affecting other tests.
    private static Study makeStudy() {
        Study study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setHealthCodeExportEnabled(true);
        study.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        study.setDataGroups(STUDY_DATA_GROUPS);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.getUserProfileAttributes().add("can_be_recontacted");
        return study;
    }
}
