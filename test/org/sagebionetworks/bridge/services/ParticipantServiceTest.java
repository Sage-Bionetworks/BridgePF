package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
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
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
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
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
    private static final Set<Roles> RESEARCH_CALLER_ROLES = ImmutableSet.of(RESEARCHER);
    private static final Set<Roles> DEV_CALLER_ROLES = ImmutableSet.of(DEVELOPER);
    private static final Set<String> CALLER_SUBS = ImmutableSet.of();
    private static final List<String> USER_LANGUAGES = ImmutableList.copyOf(BridgeUtils.commaListToOrderedSet("de,fr"));
    private static final String EMAIL = "email@email.com";
    private static final String ID = "ASDF";
    private static final String SUBSTUDY_ID = "substudyId";
    private static final DateTimeZone USER_TIME_ZONE = DateTimeZone.forOffsetHours(-3);
    private static final Map<String,String> ATTRS = new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(STUDY.getIdentifier());
    private static final SubpopulationGuid SUBPOP_GUID_1 = SubpopulationGuid.create("guid1");
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
            .withRoles(DEV_CALLER_ROLES)
            .withDataGroups(STUDY_DATA_GROUPS)
            .withAttributes(ATTRS)
            .withLanguages(USER_LANGUAGES)
            .withStatus(AccountStatus.DISABLED)
            .withExternalId(EXTERNAL_ID)
            .withTimeZone(USER_TIME_ZONE)
            .withClientData(TestUtils.getClientData()).build();
    
    private static final DateTime START_DATE = DateTime.now();
    private static final DateTime END_DATE = START_DATE.plusDays(1);
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
    private SmsService smsService;

    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private SubstudyService substudyService;
    
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
    ArgumentCaptor<Study> studyCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;

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
        participantService.setSmsService(smsService);
        participantService.setSubpopulationService(subpopService);
        participantService.setUserConsent(consentService);
        participantService.setCacheProvider(cacheProvider);
        participantService.setExternalIdService(externalIdService);
        participantService.setScheduledActivityDao(activityDao);
        participantService.setUploadService(uploadService);
        participantService.setNotificationsService(notificationsService);
        participantService.setScheduledActivityService(scheduledActivityService);
        participantService.setAccountWorkflowService(accountWorkflowService);
        participantService.setSubstudyService(substudyService);
        participantService.setActivityEventService(activityEventService);

        account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        // In order to verify that the lambda has been executed
        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(2);
            if (accountConsumer != null) {
                accountConsumer.accept(account);    
            }
            return null;
        }).when(accountDao).createAccount(any(), any(), any());
        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(account);    
            }
            return null;
        }).when(accountDao).updateAccount(any(), any());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerSubstudies(CALLER_SUBS).build());
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    private void mockAccountRetrievalWithSubstudyD() {
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy as = AccountSubstudy.create(STUDY.getIdentifier(), "substudyD", ID);
        as.setExternalId(EXTERNAL_ID);
        account.setAccountSubstudies(Sets.newHashSet(as));
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null);
    }
    
    private void mockHealthCodeAndAccountRetrieval(String email, Phone phone) {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        account.setEmail(email);
        account.setPhone(phone);
        account.setExternalId(EXTERNAL_ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        when(accountDao.constructAccount(any(), any(), any(), any(), any())).thenReturn(account);
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
    }
    
    private void mockAccountNoEmail() {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
    }
    
    @Test
    public void createParticipant() {
        STUDY.setExternalIdValidationEnabled(true);
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        IdentifierHolder idHolder = participantService.createParticipant(STUDY, PARTICIPANT, true);
        assertEquals(ID, idHolder.getIdentifier());
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(externalIdService).commitAssignExternalId(EXT_ID);
        
        // suppress email (true) == sendEmail (false)
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), any());
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, EMAIL);
        
        Account account = accountCaptor.getValue();
        assertEquals(FIRST_NAME, account.getFirstName());
        assertEquals(LAST_NAME, account.getLastName());
        assertEquals("true", account.getAttributes().get("can_be_recontacted"));
        assertEquals(DEV_CALLER_ROLES, account.getRoles());
        assertEquals(TestUtils.getClientData(), account.getClientData());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        assertEquals(Boolean.TRUE, account.getNotifyByEmail());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getTimeZone());
        assertEquals(Sets.newHashSet("group1","group2"), account.getDataGroups());
        assertEquals(ImmutableList.of("de","fr"), account.getLanguages());
        
        // don't update cache
        Mockito.verifyNoMoreInteractions(cacheProvider);
    }

    @Test
    public void createParticipantTransfersSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, participant, false);
        
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), any());
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(2, accountSubstudies.size());
        
        AccountSubstudy substudyA = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        assertEquals(STUDY.getIdentifier(), substudyA.getStudyId());
        assertEquals("substudyA", substudyA.getSubstudyId());
        assertEquals(ID, substudyA.getAccountId());
        
        AccountSubstudy substudyB = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyB")).findAny().get();
        assertEquals(STUDY.getIdentifier(), substudyB.getStudyId());
        assertEquals("substudyB", substudyB.getSubstudyId());
        assertEquals(ID, substudyB.getAccountId());
    }

    @Test
    public void createParticipantWithExternalIdValidation() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID))
            .thenReturn(Optional.of(EXT_ID));
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        // The order of these calls matters.
        InOrder inOrder = Mockito.inOrder(accountDao, externalIdService);
        inOrder.verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        inOrder.verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), any());
        inOrder.verify(externalIdService).commitAssignExternalId(EXT_ID);
        
        Account account = accountCaptor.getValue();
        assertEquals(EXTERNAL_ID, account.getExternalId());
    }

    @Test
    public void createParticipantWithInvalidParticipant() {
        // It doesn't get more invalid than this...
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        try {
            participantService.createParticipant(STUDY, participant, false);
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
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailDisabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withPhone(null).build();
        
        participantService.createParticipant(STUDY, participant, true);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailEnabledNoVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
    }
    
    @Test
    public void createParticipantEmailEnabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();

        participantService.createParticipant(STUDY, PARTICIPANT, true);

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

        participantService.createParticipant(study, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
    }

    @Test
    public void createParticipantPhoneNoEmailVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setEmail(null);
        
        // Make minimal phone participant.
        StudyParticipant phoneParticipant = new StudyParticipant.Builder().withPhone(PHONE).build();
        participantService.createParticipant(STUDY, phoneParticipant, false);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertNull(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantPhoneDisabledNoVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
    }
    
    @Test
    public void createParticipantPhoneEnabledVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        STUDY.setEmailVerificationEnabled(true);
        participantService.createParticipant(STUDY, PARTICIPANT, true);

        verify(accountWorkflowService).sendPhoneVerificationToken(STUDY, ID, PHONE);
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createParticipantAutoVerificationPhoneSuppressed() {
        Study study = makeStudy();
        study.setAutoVerificationPhoneSuppressed(true);
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        study.setEmailVerificationEnabled(true);
        participantService.createParticipant(study, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createParticipantEmailNoPhoneVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE);

        // Make minimal email participant.
        StudyParticipant emailParticipant = new StudyParticipant.Builder().withEmail(EMAIL).build();
        participantService.createParticipant(STUDY, emailParticipant, false);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void createPhoneParticipant_OptInPhoneNumber() {
        // Set up and execute test.
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        participantService.createParticipant(STUDY, PARTICIPANT, false);

        // Verify calls to SmsService.
        verify(smsService).optInPhoneNumber(ID, PHONE);
    }

    @Test
    public void createParticipantExternalIdNoPasswordIsUnverified() {
        mockHealthCodeAndAccountRetrieval(null, null);
        
        StudyParticipant idParticipant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(STUDY, idParticipant, false);
        
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
        assertNull(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantExternalIdAndPasswordIsEnabled() {
        mockHealthCodeAndAccountRetrieval(null, null);
        
        StudyParticipant idParticipant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID)
                .withPassword(PASSWORD).build();
        participantService.createParticipant(STUDY, idParticipant, false);
        
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
        account.setDataGroups(TestConstants.USER_DATA_GROUPS);
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", ID);
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", ID);
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));

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
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, criteriaContext.getUserSubstudyIds());

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
        AccountId wrongAccountId = AccountId.forExternalId(STUDY.getIdentifier(), "some-junk");
        
        participantService.getParticipant(STUDY, wrongAccountId, false);
    }
    
    @Test
    public void getStudyParticipant() {
        // A lot of mocks have to be set up first, this call aggregates almost everything we know about the user
        DateTime createdOn = DateTime.now();
        account.setHealthCode(HEALTH_CODE);
        account.setStudyId(STUDY.getIdentifier());
        account.setId(ID);
        account.setCreatedOn(createdOn);
        account.setFirstName(FIRST_NAME);
        account.setLastName(LAST_NAME);
        account.setEmailVerified(Boolean.TRUE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setStatus(AccountStatus.DISABLED);
        account.getAttributes().put("attr2", "anAttribute2");
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
        AccountSubstudy acctSubstudy1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", ID);
        acctSubstudy1.setExternalId("externalIdA");
        AccountSubstudy acctSubstudy2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", ID);
        acctSubstudy2.setExternalId("externalIdB");
        AccountSubstudy acctSubstudy3 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", ID);
        // no third external ID, this one is just not in the external IDs map
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy1, acctSubstudy2, acctSubstudy3));
        
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
        assertEquals(2, participant.getExternalIds().size());
        assertEquals("externalIdA", participant.getExternalIds().get("substudyA"));
        assertEquals("externalIdB", participant.getExternalIds().get("substudyB"));
        
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
        assertEquals(ImmutableSet.of("substudyA", "substudyB", "substudyC"), criteriaContext.getUserSubstudyIds());
    }
    
    @Test
    public void getStudyParticipantFilteringSubstudiesAndExternalIds() {
        // There is a partial overlap of substudy memberships between caller and user, the substudies that are 
        // not in the intersection, and the external IDs, should be removed from the participant
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE);
        AccountSubstudy acctSubstudy1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", ID);
        acctSubstudy1.setExternalId("externalIdA");
        AccountSubstudy acctSubstudy2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", ID);
        acctSubstudy2.setExternalId("externalIdB");
        AccountSubstudy acctSubstudy3 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", ID);
        // no third external ID, this one is just not in the external IDs map
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy1, acctSubstudy2, acctSubstudy3));
        
        // Now, the caller only sees A and C
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyC")).build());
        
        StudyParticipant participant = participantService.getParticipant(STUDY, ID, false);
        assertEquals(ImmutableSet.of("substudyA", "substudyC"), participant.getSubstudyIds());
        assertEquals(ImmutableMap.of("substudyA", "externalIdA"), participant.getExternalIds());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signOutUserWhoDoesNotExist() {
        participantService.signUserOut(STUDY, ID, true);
    }

    @Test
    public void signOutUser() {
        // Need to look this up by email, not account ID
        AccountId accountId = AccountId.forEmail(STUDY.getIdentifier(), EMAIL);
        
        // Setup
        when(accountDao.getAccount(accountId)).thenReturn(account);
        account.setId(ID);

        // Execute
        participantService.signUserOut(STUDY, EMAIL, false);

        // Verify
        verify(accountDao).getAccount(accountId);
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider).removeSessionByUserId(ID);
    }

    @Test
    public void signOutUserDeleteReauthToken() {
        // Need to look this up by email, not account ID
        AccountId accountId = AccountId.forEmail(STUDY.getIdentifier(), EMAIL);
        
        // Setup
        when(accountDao.getAccount(accountId)).thenReturn(account);
        account.setId(ID);

        // Execute
        participantService.signUserOut(STUDY, EMAIL, true);

        // Verify
        verify(accountDao).getAccount(accountId);
        verify(accountDao).deleteReauthToken(accountIdCaptor.capture());
        verify(cacheProvider).removeSessionByUserId(ID);

        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountIdCaptor.getValue().getStudyId());
        assertEquals(EMAIL, accountIdCaptor.getValue().getEmail());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateParticipantWithExternalIdValidationAddingId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES).build());
        
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));

        account.setExternalId(null); // account can be updated because it's null

        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        // The order here is significant.
        InOrder inOrder = Mockito.inOrder(accountDao, externalIdService);
        inOrder.verify(accountDao).updateAccount(accountCaptor.capture(), any());
        inOrder.verify(externalIdService).commitAssignExternalId(EXT_ID);
        
        Account account = accountCaptor.getValue();
        assertEquals(FIRST_NAME, account.getFirstName());
        assertEquals(LAST_NAME, account.getLastName());
        assertEquals("true", account.getAttributes().get("can_be_recontacted"));
        assertEquals(TestUtils.getClientData(), account.getClientData());
        
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, account.getSharingScope());
        assertEquals(Boolean.TRUE, account.getNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), account.getDataGroups());
        assertEquals(ImmutableList.of("de","fr"), account.getLanguages());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getTimeZone());
    }

    @Test
    public void updateParticipantWithSameExternalIdDoesntAssignExtId() {
        mockHealthCodeAndAccountRetrieval();

        // account and participant have the same ID, so externalIdService is not called
        participantService.updateParticipant(STUDY, PARTICIPANT);
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void updateParticipantDoesNotTransferSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies);
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(2, accountSubstudies.size());
        
        // Not changed at all, because user isn't an admin
        AccountSubstudy substudyA = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyC")).findAny().get();
        assertEquals(STUDY.getIdentifier(), substudyA.getStudyId());
        assertEquals(ID, substudyA.getAccountId());
        
        AccountSubstudy substudyB = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        assertEquals(STUDY.getIdentifier(), substudyB.getStudyId());
        assertEquals(ID, substudyB.getAccountId());
    }

    @Test
    public void updateParticipantTransfersSubstudyIdsForAdmins() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, Roles.ADMIN);
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(2, accountSubstudies.size());
        
        // get() throws exception if accountSubstudy not found
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyB")).findAny().get();
    }
    
    @Test
    public void addingSubstudyToAccountClearsCache() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(ImmutableSet.of(), substudies, Roles.ADMIN);
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals(2, account.getAccountSubstudies().size());
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(cacheProvider).removeSessionByUserId(ID);
    }
    
    @Test
    public void removingSubstudyFromAccountClearsCache() { 
        Set<String> substudies = ImmutableSet.of("substudyA");
        StudyParticipant participant = mockSubstudiesInRequest(ImmutableSet.of(), substudies, Roles.ADMIN);
        
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy asA = AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID);
        account.getAccountSubstudies().add(asA);
        
        AccountSubstudy asB = AccountSubstudy.create(STUDY.getIdentifier(), "substudyB", ID);
        asB.setExternalId("extB");
        account.getAccountSubstudies().add(asB);
        
        participantService.updateParticipant(STUDY, participant);
        
        // We've tested this collection more thoroughly in updateParticipantTransfersSubstudyIdsForAdmins()
        assertEquals(1, account.getAccountSubstudies().size());
        verify(externalIdService).unassignExternalId(account, "extB");
        verify(cacheProvider).removeSessionByUserId(ID);
    }
    
    @Test
    public void updateParticipantWithoutSubstudyChangesForAdmins() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyC");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, Roles.ADMIN);
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(STUDY, participant);
        
        // We've tested this collection more thoroughly in updateParticipantTransfersSubstudyIdsForAdmins()
        verify(cacheProvider, never()).removeSessionByUserId(any());
    }    
    
    @Test
    public void updateParticipantDoesNotTransferSubstudyIdsForResearchers() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, Roles.RESEARCHER);
        
        mockHealthCodeAndAccountRetrieval();
        // These should not be changed by the update, they should be substudyC and substudyA in 
        // the persisted account.
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(STUDY.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(2, accountSubstudies.size());
        
        // get() throws exception if accountSubstudy not found
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyC")).findAny().get();
    }    

    @Test(expected = InvalidEntityException.class)
    public void updateParticipantWithInvalidParticipant() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("bogusGroup"))
                .build();
        participantService.updateParticipant(STUDY, participant);
    }
    
    @Test
    public void updateParticipantWithNoAccount() {
        doThrow(new EntityNotFoundException(Account.class)).when(accountDao).getAccount(ACCOUNT_ID);
        try {
            participantService.updateParticipant(STUDY, PARTICIPANT);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
        }
        verify(accountDao, never()).updateAccount(any(), any());
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
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        mockHealthCodeAndAccountRetrieval();
        account.setStatus(AccountStatus.ENABLED);

        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(null).build();

        participantService.updateParticipant(STUDY, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
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
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        // Validated and required, use reservation service and don't set as option
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }

    @Test
    public void limitNotExceededException() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(9);
        when(accountDao.getPagedAccountSummaries(STUDY, AccountSummarySearch.EMPTY_SEARCH))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
    }
    
    @Test
    public void throwLimitExceededExactlyException() {
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(10);
        when(accountDao.getPagedAccountSummaries(STUDY, AccountSummarySearch.EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        try {
            participantService.createParticipant(STUDY, PARTICIPANT, false);
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
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
    }
    
    @Test
    public void updateIdentifiersFiltersSubstudyPermissions() {
        // caller in question is in substudyB
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        // account in question is in substudyA
        mockHealthCodeAndAccountRetrieval();
        account.setAccountSubstudies(ImmutableSet.of(AccountSubstudy.create(STUDY.getIdentifier(), "substudyB", ID)));
        when(accountDao.authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        try {
            // And so this fails...
            IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, "newExternalId");
            participantService.updateIdentifiers(STUDY, CONTEXT, update);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals("Account not found.", e.getMessage());
        }
        verify(accountDao, never()).updateAccount(any(), any());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateIdentifiersAssignsExternalIdEvenWhenAlreadyAssigned() {
        // Fully associated external ID can be changed by an update.
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy as = AccountSubstudy.create(STUDY.getIdentifier(), "substudyB", ID);
        as.setExternalId(EXTERNAL_ID);
        account.setAccountSubstudies(Sets.newHashSet(as));
        // RequestContext already has substudyB
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        
        when(accountDao.authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        ExternalIdentifier newExtId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId");
        newExtId.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId")).thenReturn(Optional.of(newExtId));
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, "newExternalId");
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        verify(accountDao).updateAccount(eq(account), any());
        
        assertEquals(2, account.getAccountSubstudies().size());
        
        Set<String> substudyIds = account.getAccountSubstudies().stream()
                .map(AccountSubstudy::getSubstudyId).collect(Collectors.toSet());
        Set<String> externalIds = account.getAccountSubstudies().stream()
                .map(AccountSubstudy::getExternalId).collect(Collectors.toSet());
        
        assertEquals(ImmutableSet.of("substudyA", "substudyB"), substudyIds);
        assertEquals(ImmutableSet.of(EXTERNAL_ID, "newExternalId"), externalIds);
        
        // The RequestContext should be updated
        assertEquals(ImmutableSet.of("substudyA", "substudyB"), BridgeUtils.getRequestContext().getCallerSubstudies());
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
        verify(accountDao).updateAccount(eq(account), eq(null));
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
        verify(accountDao).updateAccount(eq(account), eq(null));
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
        ExternalIdentifier differentExternalId = ExternalIdentifier.create(TestConstants.TEST_STUDY, "extid");
        
        mockAccountNoEmail();
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(externalIdService.getExternalId(any(), eq("extid"))).thenReturn(Optional.of(differentExternalId));
        
        STUDY.setEmailVerificationEnabled(false);
        STUDY.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, null, null, "extid");
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        assertEquals("extid", account.getExternalId());
        verify(externalIdService).commitAssignExternalId(differentExternalId);
    }

    @Test
    public void updateIdentifiersAuthenticatingToAnotherAccountInvalid() {
        // This ID does not match the ID in the request's context, and that will fail
        account.setId("another-user-id");
        when(accountDao.authenticate(STUDY, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
        
        try {
            participantService.updateIdentifiers(STUDY, CONTEXT, update);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(accountDao, never()).updateAccount(any(), any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
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
        
        // Now that an external ID addition will simply add another external ID, the 
        // test has been changed to submit an existing external ID.
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "updated@email.com",
                new Phone("4082588569", "US"), EXTERNAL_ID);
        
        participantService.updateIdentifiers(STUDY, CONTEXT, update);
        
        // None of these have changed.
        assertEquals(EMAIL, account.getEmail());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        assertEquals(TestConstants.PHONE, account.getPhone());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(accountDao, never()).updateAccount(any(), any());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
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
        verify(accountDao).updateAccount(any(), eq(null));
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }

    @Test(expected = InvalidEntityException.class)
    public void createRequiredExternalIdValidated() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withRoles(Sets.newHashSet())
                .withExternalId(null).build();
        
        participantService.createParticipant(STUDY, participant, false);
    }

    @Test
    public void createRequiredExternalIdWithRolesOK() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdRequiredOnSignup(true);
        
        // developer
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withExternalId(null).build();
        
        participantService.createParticipant(STUDY, participant, false);
        // called with null, which does nothing.
        verify(externalIdService).commitAssignExternalId(null);
    }
    
    @Test
    public void createManagedExternalIdOK() {
        mockHealthCodeAndAccountRetrieval();
        
        STUDY.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }

    @Test
    public void badManagedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.empty());
        
        try {
            participantService.createParticipant(STUDY, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(accountDao, never()).createAccount(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }

    @Test
    public void usedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        identifier.setHealthCode("AAA");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(identifier));
        
        try {
            participantService.createParticipant(STUDY, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
            verify(accountDao, never()).createAccount(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
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
        participantService.updateParticipant(STUDY, participant);
    }
    
    @Test
    public void addingManagedExternalIdOnUpdateOK() {
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(identifier));
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }

    @Test
    public void updatingBlankExternalIdFails() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT)
                .withExternalId("").build();
        try {
            participantService.updateParticipant(STUDY, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            
        }
        verify(externalIdService, never()).commitAssignExternalId(any());
    }

    @Test
    public void changingManagedExternalIdWorks() {
        ExternalIdentifier newExternalId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId");
        newExternalId.setHealthCode(HEALTH_CODE);
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId"))
                .thenReturn(Optional.of(identifier));
        
        // This record has a different external ID than the mocked account
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals("newExternalId", account.getExternalId());
        verify(externalIdService).commitAssignExternalId(newExternalId);
    }

    @Test
    public void updateParticipantWithNoExternalIdDoesNotChangeExistingId() {
        mockHealthCodeAndAccountRetrieval();

        // Participant has no external ID, so externalIdService is not called
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, participant);
        verify(externalIdService, never()).commitAssignExternalId(any());
        assertEquals(EXTERNAL_ID, account.getExternalId());
    }

    @Test
    public void sameManagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        ExternalIdentifier identifier = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(identifier));
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    // Removed because you can no longer simply remove an external ID
    // public void removingManagedExternalIdWorks();
    
    @Test
    public void createUnmanagedExternalIdWillAssign() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.empty());
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(accountDao).createAccount(any(), accountCaptor.capture(), any());
        assertEquals(EXTERNAL_ID, accountCaptor.getValue().getExternalId());
        
        verify(externalIdService).commitAssignExternalId(null);
    }

    @Test
    public void addingUnmanagedExternalIdOnUpdateOK() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        account.setExternalId(null);
        
        participantService.updateParticipant(STUDY, PARTICIPANT);

        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).commitAssignExternalId(null);
    }

    @Test
    public void sameUnmanagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void changingUnmanagedExternalIdWorks() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals("newExternalId", account.getExternalId());
        // called with null, which does nothing.
        verify(externalIdService).commitAssignExternalId(null);
    }

    @Test
    public void removingUnmanagedExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void changingExternalIdOnlyWorksForResearcher() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("someOtherId").build();
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void removingExternalIdOnlyWorksForResearcher() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(null).build();
        participantService.updateParticipant(STUDY, participant);
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    private StudyParticipant.Builder withParticipant() {
        return new StudyParticipant.Builder().copyOf(PARTICIPANT);
    }
    
    @Test
    public void createParticipantValidatesUnmanagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = withParticipant().withExternalId("  ").build();
        
        try {
            participantService.createParticipant(STUDY, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("externalId cannot be blank", e.getErrors().get("externalId").get(0));
        }
    }
    
    @Test
    public void createParticipantValidatesManagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        try {
            participantService.createParticipant(STUDY, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("externalId is not a valid external ID", e.getErrors().get("externalId").get(0));
        }
    }
    @Test
    public void createParticipantNoExternalIdAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(accountDao.constructAccount(STUDY, EMAIL, PHONE, null, PASSWORD)).thenReturn(account);
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.createParticipant(STUDY, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, null, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), eq(account), any());
        verify(externalIdService).commitAssignExternalId(null);
    }
    @Test
    public void createParticipantExternalIdAddedUpdatesExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(accountDao.constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD)).thenReturn(account);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.createParticipant(STUDY, PARTICIPANT, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), eq(account), any());
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }
    @Test
    public void updateParticipantValidatesManagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null); // adding external ID that is not in system.
        
        try {
            participantService.updateParticipant(STUDY, PARTICIPANT);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("externalId is not a valid external ID", e.getErrors().get("externalId").get(0));
        }        
    }
    @Test
    public void updateParticipantValidatesUnmanagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null); // adding external ID that is not in system.
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(" ").build();
            participantService.updateParticipant(STUDY, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("externalId cannot be blank", e.getErrors().get("externalId").get(0));
        }        
    }
    @Test
    public void updateParticipantNoExternalIdsNoneAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountDao).updateAccount(account, null);
        assertNull(account.getExternalId());
    }
    @SuppressWarnings("unchecked")
    @Test
    public void updateParticipantNoExternalIdsOneAddedUpdates() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        verify(accountDao).updateAccount(eq(account), any());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }
    @Test
    public void updateParticipantExternalIdsExistNoneAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        mockAccountRetrievalWithSubstudyD();
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountDao).updateAccount(account, null);
        assertEquals(EXTERNAL_ID, account.getExternalId()); // not erased
    }
    @Test
    public void updateParticipantExternalIdsExistOneAddedDoesNothing() {
        // For normal users, adding an external ID when one already exists currently doesn't succeed.
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        STUDY.setExternalIdValidationEnabled(true);
        mockAccountRetrievalWithSubstudyD();
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId"))
                .thenReturn(Optional.of(ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId")));
        
        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountDao).updateAccount(account, null);
        assertEquals(EXTERNAL_ID, account.getExternalId()); // not changed
    }
    @Test
    public void updateParticipantAsResearcherNoExternalIdsNoneAddedDoesNothing() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountDao).updateAccount(account, null);
        assertNull(account.getExternalId());
    }
    @SuppressWarnings("unchecked")
    @Test
    public void updateParticipantAsResearcherNoExternalIdsOneAddedUpdates() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        verify(accountDao).updateAccount(eq(account), any());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }
    @SuppressWarnings("unchecked")
    @Test
    public void updateParticipantAsResearcherExternalIdsExistNoneMatchOneAddedUpdates() {
        STUDY.setExternalIdValidationEnabled(true);
        mockAccountRetrievalWithSubstudyD();
        account.setExternalId(null);
        
        ExternalIdentifier nextExtId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId"))
            .thenReturn(Optional.of(nextExtId));

        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(eq(account), any());
        assertEquals("newExternalId", account.getExternalId());
        verify(externalIdService).commitAssignExternalId(nextExtId);
    }
    @Test
    public void updateParticipantAsResearcherExternalIdsExistAndMatchOneAddedDoesNothing() {
        STUDY.setExternalIdValidationEnabled(true);
        mockAccountRetrievalWithSubstudyD();
        account.setExternalId(null);

        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));

        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountDao).updateAccount(account, null);
        assertNull(account.getExternalId());
    }
    
    @Test
    public void sendSmsMessage() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setHealthCode(HEALTH_CODE);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        SmsTemplate template = new SmsTemplate("This is a test ${studyShortName}"); 
        
        participantService.sendSmsMessage(STUDY, ID, template);

        verify(smsService).sendSmsMessage(eq(ID), providerCaptor.capture());
        
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
        SmsTemplate template = new SmsTemplate("    "); 
        
        participantService.sendSmsMessage(STUDY, ID, template);
    }
    
    @Test
    public void getActivityEvents() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityEvents(STUDY, ID);
        
        verify(activityEventService).getActivityEventList(HEALTH_CODE);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void normalUserCanAddExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        
        participantService.updateParticipant(STUDY, PARTICIPANT);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), any());
        assertEquals(EXTERNAL_ID, accountCaptor.getValue().getExternalId());
    }
    
    @Test
    public void normalUserCannotChangeExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        assertEquals(EXTERNAL_ID, accountCaptor.getValue().getExternalId());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void researcherCanChangeUnmanagedExternalIdOnUpdate() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();

        participantService.updateParticipant(STUDY, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), any());
        assertEquals("newExternalId", accountCaptor.getValue().getExternalId());
        // But they will not assign because there's no record to assign.
        verify(externalIdService).commitAssignExternalId(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void researcherCanChangeManagedExternalIdOnUpdate() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setExternalIdValidationEnabled(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("newExternalId").build();
        ExternalIdentifier newExtId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), "newExternalId");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), "newExternalId"))
                .thenReturn(Optional.of(newExtId));

        participantService.updateParticipant(STUDY, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), any());
        assertEquals("newExternalId", accountCaptor.getValue().getExternalId());
        verify(externalIdService).commitAssignExternalId(newExtId);
    }
    
    @Test
    public void assignExternalId() {
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        participantService.assignExternalId(ACCOUNT_ID, EXT_ID);
        
        verify(externalIdService).commitAssignExternalId(EXT_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void assignExternalIdFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.assignExternalId(ACCOUNT_ID, EXT_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void createSmsRegistrationFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.createSmsRegistration(STUDY, ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantWithAccountFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.getParticipant(STUDY, account, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantWithAccountIdFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.getParticipant(STUDY, ACCOUNT_ID, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantWithUserIdFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());

        participantService.getParticipant(STUDY, ID, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signUserOutFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        // Need to look this up by email, not account ID
        AccountId accountId = AccountId.forEmail(STUDY.getIdentifier(), EMAIL);
        
        when(accountDao.getAccount(accountId)).thenReturn(account);
        account.setId("userId");

        participantService.signUserOut(STUDY, EMAIL, true);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateParticipantFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        // The persisted account has substudyD, the update has substudyD, but the 
        // caller does not have this substudy, so it appears as a 404 (ENFE).
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withSubstudyIds(ImmutableSet.of("substudyD")).build();
        
        participantService.updateParticipant(STUDY, participant);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityHistoryFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.getActivityHistory(STUDY, ID, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY, PAGE_SIZE);

        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY,
                PAGE_SIZE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteActivitiesFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.deleteActivities(STUDY, ID);
        
        verify(activityDao).deleteActivitiesForUser(HEALTH_CODE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getUploadsFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        DateTime startTime = DateTime.parse("2015-11-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2015-11-01T23:59:59.999Z");
        
        participantService.getUploads(STUDY, ID, startTime, endTime, 10, "ABC");
        
        verify(uploadService).getUploads(HEALTH_CODE, startTime, endTime, 10, "ABC");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void listRegistrationsFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.listRegistrations(STUDY, ID);
        
        verify(notificationsService).listRegistrations(HEALTH_CODE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void sendNotificationFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        participantService.sendNotification(STUDY, ID, message);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void sendSmsMessageFiltersSubstudies() {
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setHealthCode(HEALTH_CODE);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        account.setAccountSubstudies(ImmutableSet.of(
                AccountSubstudy.create(STUDY.getIdentifier(), "substudyD", ID)));
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        SmsTemplate template = new SmsTemplate("This is a test ${studyShortName}"); 
        
        participantService.sendSmsMessage(STUDY, ID, template);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityEventsFiltersSubstudies() {
        mockAccountRetrievalWithSubstudyD();
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        participantService.getActivityEvents(STUDY, ID);
        
        verify(activityEventService).getActivityEventList(HEALTH_CODE);      
    }
    
    @Test
    public void beginAssignExternalId() {
        Account account = Account.create();
        account.setId(ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, EXTERNAL_ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, EXTERNAL_ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, EXTERNAL_ID);
        
        assertEquals(EXTERNAL_ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(SUBSTUDY_ID, externalId.getSubstudyId());
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertEquals(1, account.getAccountSubstudies().size());
        
        AccountSubstudy accountSubstudy = Iterables.getFirst(account.getAccountSubstudies(), null);
        assertEquals(ID, accountSubstudy.getAccountId());
        assertEquals(EXTERNAL_ID, accountSubstudy.getExternalId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountSubstudy.getStudyId());
        assertEquals(SUBSTUDY_ID, accountSubstudy.getSubstudyId());
    }    
    
    @Test
    public void beginAssignExternalIdIdentifierMissing() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, null);
        assertNull(externalId);
    }
    
    @Test
    public void beginAssignExternalIdIdentifierObjectMissing() {
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.empty());
        
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, ID);
        assertNull(externalId);
    }
    
    @Test
    public void beginAssignExternalIdHealthCodeExistsEqual() {
        Account account = Account.create();
        account.setId(ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, EXTERNAL_ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        existing.setHealthCode(HEALTH_CODE); // despite assignment, we update everything
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, EXTERNAL_ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, EXTERNAL_ID);
        
        assertEquals(EXTERNAL_ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(SUBSTUDY_ID, externalId.getSubstudyId());
        
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertEquals(1, account.getAccountSubstudies().size());
        
        AccountSubstudy accountSubstudy = Iterables.getFirst(account.getAccountSubstudies(), null);
        assertEquals(ID, accountSubstudy.getAccountId());
        assertEquals(EXTERNAL_ID, accountSubstudy.getExternalId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountSubstudy.getStudyId());
        assertEquals(SUBSTUDY_ID, accountSubstudy.getSubstudyId());
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void beginAssignExternalIdHealthCodeExistsNotEqual() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        existing.setHealthCode("anotherHealthCode");
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(existing));
        
        participantService.beginAssignExternalId(account, ID);
    }
    
    @Test
    public void beginAssignExternalIdWithoutSubstudy() {
        Account account = Account.create();
        account.setId(ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, ID);
        
        assertEquals(ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(ID, account.getExternalId());
        assertNull(externalId.getSubstudyId());
        assertTrue(account.getAccountSubstudies().isEmpty());
    }
    
    @Test
    public void beginAssignExternalIdAccountHasSingleSubstudyId() {
        Account account = Account.create();
        account.setId(ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        account.setExternalId("legacyExternalId");
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, ID);
        
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        
        // Not changed, but the new external ID is still recorded along with the substudy association
        assertEquals("legacyExternalId", account.getExternalId());
        assertEquals(1, account.getAccountSubstudies().size());
        
        AccountSubstudy accountSubstudy = Iterables.getFirst(account.getAccountSubstudies(), null);
        assertEquals(ID, accountSubstudy.getExternalId());
    }
    
    @Test
    public void rollbackCreateParticipantWhenAccountCreationFails() {
        when(accountDao.constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD)).thenReturn(account);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        doThrow(new ConcurrentModificationException("")).when(accountDao).createAccount(eq(STUDY), eq(account), any());
        
        try {
            participantService.createParticipant(STUDY, PARTICIPANT, false);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
    }
    
    @Test
    public void rollbackUpdateParticipantWhenAccountUpdateFails() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        doThrow(new ConcurrentModificationException("")).when(accountDao).updateAccount(eq(account), any());
        
        try {
            participantService.updateParticipant(STUDY, PARTICIPANT);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
    }
    
    @Test
    public void rollbackUpdateIdentifiersWhenAccountUpdateFails() {
        mockHealthCodeAndAccountRetrieval();
        account.setExternalId(null);
        account.setAccountSubstudies(Sets.newHashSet());
        when(accountDao.authenticate(STUDY, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        ExternalIdentifier extId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXTERNAL_ID);
        extId.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(STUDY.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        // Now... accountDao throws an exception
        doThrow(new ConcurrentModificationException("")).when(accountDao).updateAccount(eq(account), any());
        try {
            IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, EXTERNAL_ID);
            participantService.updateIdentifiers(STUDY, CONTEXT, update);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
    }
    
    // getPagedAccountSummaries() filters substudies in the query itself, as this is the only 
    // way to get correct paging.
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> callerRoles, boolean canSetStatus) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(AccountStatus.ENABLED).build();
        
        participantService.updateParticipant(STUDY, participant);

        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
        Account account = accountCaptor.getValue();

        if (canSetStatus) {
            assertEquals(AccountStatus.ENABLED, account.getStatus());
        } else {
            assertNull(account.getStatus());
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(STUDY, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, EXTERNAL_ID, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), any());
        Account account = accountCaptor.getValue();
        
        if (rolesThatAreSet != null) {
            assertEquals(rolesThatAreSet, account.getRoles());
        } else {
            assertEquals(Sets.newHashSet(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet, Set<Roles> expected) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());

        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(rolesThatAreSet).build();
        participantService.updateParticipant(STUDY, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture(), eq(null));
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
    
    private StudyParticipant mockSubstudiesInRequest(Set<String> callerSubstudies, Set<String> participantSubstudies, Roles... callerRoles) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies)
                .withCallerRoles( (callerRoles.length == 0) ? null : ImmutableSet.copyOf(callerRoles)).build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withSubstudyIds(participantSubstudies).build();
        
        for (String substudyId : callerSubstudies) {
            when(substudyService.getSubstudy(STUDY.getStudyIdentifier(), substudyId, false)).thenReturn(Substudy.create());    
        }
        for (String substudyId : participantSubstudies) {
            when(substudyService.getSubstudy(STUDY.getStudyIdentifier(), substudyId, false)).thenReturn(Substudy.create());    
        }
        return participant;
    }
}
