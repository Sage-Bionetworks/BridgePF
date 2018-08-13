package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
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
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

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
    private static final String EVENT_ID = "enrollment";
    private static final DateTimeZone USER_TIME_ZONE = DateTimeZone.forOffsetHours(-3);
    private static final Map<String,String> ATTRS = new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(STUDY.getIdentifier());
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
    private static final DateTime EVENT_TIMESTAMP = START_DATE;
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withUserId(ID).withStudyIdentifier(TestConstants.TEST_STUDY).build();
    private static final SignIn EMAIL_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withReauthToken("ASDF").build();
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
    
    @Mock
    private ActivityEventService activityEventService;
    
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
    ArgumentCaptor<ActivityEvent> eventCaptor;
    
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
        participantService.setActivityEventService(activityEventService);
        
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
    
    private void mockAccountNoEmail() {
        TestUtils.mockEditAccount(accountDao, account);
        ((GenericAccount)account).setId(ID);
        ((GenericAccount)account).setHealthCode(HEALTH_CODE);
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
        account.setConsentSignatureHistory(SubpopulationGuid.create("guid1"), sigs1);
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
        when(subpopService.getSubpopulations(STUDY.getStudyIdentifier())).thenReturn(subpopulations);

        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SubpopulationGuid.create("guid1"))).thenReturn(subpop1);
        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SubpopulationGuid.create("guid2"))).thenReturn(subpop2);
        
        // Get the fully initialized participant object (including histories)
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);
        
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
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
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

        // Paticipant has no external ID, so externalIdService is not called
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateParticipantWithInvalidParticipant() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("bogusGroup"))
                .build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
    }
    
    @Test
    public void updateParticipantWithNoAccount() {
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

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
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
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, false);

        assertTrue(participant.getConsentHistories().keySet().isEmpty());
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
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(STUDY.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, true);

        assertEquals(1, participant.getConsentHistories().keySet().size());
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
        
        participantService.withdrawAllConsents(STUDY, ID, withdrawal, withdrewOn);
        
        verify(consentService).withdrawAllConsents(eq(STUDY), participantCaptor.capture(),
            contextCaptor.capture(), eq(withdrawal), eq(withdrewOn));
        assertEquals(ID, participantCaptor.getValue().getId());
        assertEquals(ID, contextCaptor.getValue().getUserId());
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
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        participantService.sendNotification(STUDY, ID, message);
        
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
    

    @Test
    public void updateIdentifiersEmailSignInUpdatePhone() {
        // Verifies email-based sign in, phone update, account update, and an updated 
        // participant is returned... the common happy path.
        mockHealthCodeAndAccountRetrieval();
        when(accountDao.authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, TestConstants.PHONE, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals(TestConstants.PHONE, account.getPhone());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        verify(accountDao).authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN);
        verify(accountDao).updateAccount(account, true);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(PARTICIPANT.getId(), returned.getId());
    }
    
    @Test
    public void updateIdentifiersPhoneSignInUpdateEmail() {
        // This flips the method of sign in to use a phone, and sends an email update. 
        // Also tests the common path of creating unverified email address with verification email sent
        mockAccountNoEmail();
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        STUDY.setEmailVerificationEnabled(true);
        STUDY.setAutoVerificationEmailSuppressed(false);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals("email@email.com", account.getEmail());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        verify(accountDao).authenticate(STUDY, PHONE_PASSWORD_SIGN_IN);
        verify(accountDao).updateAccount(account, true);
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, "email@email.com");
        assertEquals(PARTICIPANT.getId(), returned.getId());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateIdentifiersValidates() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, null);
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithBlankEmail() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, "", null, null);
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithBlankExternalId() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, " ");
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
    }

    @Test(expected = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithInvalidPhone() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("US", "1231231234"), null);
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
    }
    
    @Test
    public void updateIdentifiersUsingReauthentication() {
        mockHealthCodeAndAccountRetrieval();
        when(accountDao.reauthenticate(STUDY, REAUTH_REQUEST)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(REAUTH_REQUEST, null, TestConstants.PHONE, null);
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        verify(accountDao).reauthenticate(STUDY, REAUTH_REQUEST);
    }

    @Test
    public void updateIdentifiersCreatesVerifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        STUDY.setEmailVerificationEnabled(false);
        STUDY.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);

        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals("email@email.com", account.getEmail());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersCreatesUnverifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        STUDY.setEmailVerificationEnabled(true);
        STUDY.setAutoVerificationEmailSuppressed(true);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, EMAIL, null, null);
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals(EMAIL, account.getEmail());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersCreatesExternalIdWithAssignment() {
        mockAccountNoEmail();
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        STUDY.setEmailVerificationEnabled(false);
        STUDY.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, null, null, "extid");
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals("extid", account.getExternalId());
        verify(externalIdService).assignExternalId(STUDY, "extid", HEALTH_CODE);
    }
    
    @Test
    public void updateIdentifiersAuthenticatingToAnotherAccountInvalid() {
        // This ID does not match the ID in the request's context, and that will fail
        ((GenericAccount)account).setId("another-user-id");
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
        
        try {
            participantService.updateIdentifiers(STUDY, CONTEXT, update);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(accountDao, never()).updateAccount(any(), Mockito.anyBoolean());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
            verify(externalIdService, never()).assignExternalId(any(), any(), any());
        }
    }
    
    @Test
    public void updateIdentifiersDoNotOverwriteExistingIdentifiers() {
        mockHealthCodeAndAccountRetrieval();
        account.setEmailVerified(Boolean.TRUE);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(Boolean.TRUE);
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "updated@email.com",
                new Phone("4082588569", "US"), "newExternalId");
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        // None of these have changed.
        assertEquals(EMAIL, account.getEmail());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        assertEquals(TestConstants.PHONE, account.getPhone());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(accountDao, never()).updateAccount(any(), Mockito.anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).assignExternalId(STUDY, account.getExternalId(), account.getHealthCode());
    }
    
    @Test
    public void updateIdentifiersDoesNotReassignExistingExternalId() throws Exception {
        mockHealthCodeAndAccountRetrieval();
        when(accountDao.authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountDao.getAccount(any())).thenReturn(account);
        
        // Add phone
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("4082588569", "US"),
                null);
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        // External ID not changed, externalIdService not called
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(accountDao).updateAccount(any(), eq(true));
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).assignExternalId(STUDY, account.getExternalId(), account.getHealthCode());
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
    
    @SuppressWarnings("unchecked")
    @Test
    public void usedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        identifier.setHealthCode("AAA");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(identifier);
        
        when(accountDao.createAccount(any(), any())).thenThrow(new EntityAlreadyExistsException(Account.class, (String)null, (Map)null));
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
    public void addingManagedExternalIdOnUpdateOK() {
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
    public void changingManagedExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId")).thenReturn(identifier);
        
        // This record has a different external ID than the mocked accound
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
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
    public void removingManagedExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId")).thenReturn(identifier);
        
        // This record has a different external ID than the mocked accound
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
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
    public void addingUnmanagedExternalIdOnUpdateOK() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        account.setExternalId(null);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
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
    public void changingUnmanagedExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
    }
    
    @Test
    public void removingUnmanagedExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, CALLER_ROLES, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).assignExternalId(any(), any(), any());
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
    public void getActivityEvents() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityEvents(STUDY, ID);
        
        verify(activityEventService).getActivityEventList(HEALTH_CODE);
    }
    
    @Test
    public void deleteAllActivityEvents() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.deleteAllActivityEvents(STUDY, ID);
        
        verify(activityEventService).deleteActivityEvents(HEALTH_CODE);
    }
    
    @Test
    public void deleteActivityEvent() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.deleteActivityEvent(STUDY, ID, EVENT_ID);
        
        verify(activityEventService).deleteActivityEvent(HEALTH_CODE, EVENT_ID);
    }

    @Test
    public void publishActivityEvent() {
        mockHealthCodeAndAccountRetrieval();
        
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey(EVENT_ID)
                .withTimestamp(EVENT_TIMESTAMP)
                .withAnswerValue("answer").build();

        participantService.publishActivityEvent(STUDY, ID, request);
        
        verify(activityEventService).publishActivityEvent(eventCaptor.capture(), eq(false));
        
        ActivityEvent event = eventCaptor.getValue();
        assertEquals(EVENT_ID, event.getEventId());
        assertEquals(HEALTH_CODE, event.getHealthCode());
        assertEquals(new Long(EVENT_TIMESTAMP.getMillis()), event.getTimestamp());
        assertEquals("answer", event.getAnswerValue());
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteActivityEventNoEventId() { 
        mockHealthCodeAndAccountRetrieval();
        participantService.deleteActivityEvent(STUDY, ID, "  ");
    }

    @Test(expected = BadRequestException.class)
    public void updateActivityEventNoEvent() {
        mockHealthCodeAndAccountRetrieval();
        participantService.publishActivityEvent(STUDY, ID, null);
    }

    @Test(expected = BadRequestException.class)
    public void updateActivityEventNoEventId() {
        mockHealthCodeAndAccountRetrieval();
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withTimestamp(EVENT_TIMESTAMP).build();
        participantService.publishActivityEvent(STUDY, ID, request);
    }
    
    @Test(expected = BadRequestException.class)
    public void updateActivityEventNoTimestamp() {
        mockHealthCodeAndAccountRetrieval();
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey(EVENT_ID).build();
        participantService.publishActivityEvent(STUDY, ID, request);
    }
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> roles, boolean canSetStatus) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(AccountStatus.ENABLED).build();
        
        participantService.updateParticipant(STUDY, roles, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
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
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(false));
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
