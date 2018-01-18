package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;
import static org.sagebionetworks.bridge.dao.ParticipantOption.TIME_ZONE;
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
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2");
    private static final long CONSENT_PUBLICATION_DATE = DateTime.now().getMillis();
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Phone OTHER_PHONE = new Phone("4082588569", "US");
    private static final Study STUDY = new DynamoStudy();
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
    
    private ParticipantService participantService;
    
    @Mock
    private AccountDao accountDao;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private ParticipantOptionsService optionsService;
    
    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private Account account;
    
    @Mock
    private ParticipantOptionsLookup lookup;
    
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
    ArgumentCaptor<Map<ParticipantOption,String>> optionsCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @Captor
    ArgumentCaptor<Set<Roles>> rolesCaptor;

    @Captor
    ArgumentCaptor<UserSession> sessionCaptor;
    
    @Captor
    ArgumentCaptor<Email> emailCaptor;
    
    @Captor
    ArgumentCaptor<Study> studyCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;
    
    @Before
    public void before() {
        STUDY.setExternalIdValidationEnabled(false);
        STUDY.setExternalIdRequiredOnSignup(false);
        STUDY.setAccountLimit(0);
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
        participantService.setParticipantOptionsService(optionsService);
        participantService.setSubpopulationService(subpopService);
        participantService.setUserConsent(consentService);
        participantService.setCacheProvider(cacheProvider);
        participantService.setExternalIdService(externalIdService);
        participantService.setScheduledActivityDao(activityDao);
        participantService.setUploadService(uploadService);
        participantService.setNotificationsService(notificationsService);
        participantService.setScheduledActivityService(scheduledActivityService);
        participantService.setAccountWorkflowService(accountWorkflowService);
    }
    
    private void mockHealthCodeAndAccountRetrieval(String email, Phone phone) {
        when(account.getId()).thenReturn(ID);
        when(accountDao.constructAccount(eq(STUDY), any(), any(), eq(PASSWORD))).thenReturn(account);
        when(accountDao.createAccount(same(STUDY), same(account))).thenReturn(ID);
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(account.getEmail()).thenReturn(email);
        when(account.getPhone()).thenReturn(phone);
        when(optionsService.getOptions(HEALTH_CODE)).thenReturn(lookup);
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE);
    }
    
    @Test
    public void createParticipant() {
        STUDY.setExternalIdValidationEnabled(true);
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        IdentifierHolder idHolder = participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);
        assertEquals(ID, idHolder.getIdentifier());
        
        verify(externalIdService).reserveExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, PASSWORD);
        // suppress email (true) == sendEmail (false)
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture());
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, EMAIL);
        
        Map<ParticipantOption, String> options = optionsCaptor.getValue();
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), options.get(SHARING_SCOPE));
        assertEquals("true", options.get(EMAIL_NOTIFICATIONS));
        // Because strict validation is enabled, we do not update this property along with the others, we
        // go through externalIdService
        assertNull(options.get(EXTERNAL_IDENTIFIER));
        assertNull(options.get(TIME_ZONE));
        assertTrue(options.get(DATA_GROUPS).contains("group1"));
        assertTrue(options.get(DATA_GROUPS).contains("group2"));
        assertTrue(options.get(LANGUAGES).contains("de"));
        assertTrue(options.get(LANGUAGES).contains("fr"));
        
        Account account = accountCaptor.getValue();
        verify(account).setFirstName(FIRST_NAME);
        verify(account).setLastName(LAST_NAME);
        verify(account).setAttribute("can_be_recontacted", "true");
        verify(account).setRoles(USER_ROLES);
        verify(account).setClientData(TestUtils.getClientData());
        verify(account).setStatus(AccountStatus.UNVERIFIED);
        
        // don't update cache
        verify(cacheProvider, never()).removeSessionByUserId(anyString());
    }
    
    // Or any other failure to reserve an externalId
    @Test
    public void createParticipantWithAssignedId() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        doThrow(new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", "AAA"))
            .when(externalIdService).reserveExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
        }
        verify(externalIdService).reserveExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, PASSWORD);
        verifyNoMoreInteractions(optionsService);
    }
    
    @Test
    public void createParticipantWithExternalIdValidation() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        verify(externalIdService).reserveExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        // Do not set the externalId with the other options, go through the externalIdService
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        Map<ParticipantOption,String> options = optionsCaptor.getValue();
        assertNull(options.get(EXTERNAL_IDENTIFIER));
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
        verifyNoMoreInteractions(optionsService);
        verifyNoMoreInteractions(externalIdService);
    }
    
    @Test
    public void createParticipantEmailDisabledNoVerificationWanted() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(account).setStatus(AccountStatus.ENABLED);
        verify(account).setEmailVerified(Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailDisabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(account).setStatus(AccountStatus.ENABLED);
        verify(account).setEmailVerified(Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailEnabledNoVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(account).setStatus(AccountStatus.ENABLED);
        verify(account).setEmailVerified(Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailEnabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, true);
        
        verify(accountWorkflowService).sendEmailVerificationToken(any(), any(), any());
        verify(account).setStatus(AccountStatus.UNVERIFIED);
        verify(account).setEmailVerified(Boolean.FALSE);
    }
    
    @Test
    public void createParticipantWithPhoneEmailEnabledVerificationWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval(null, null);

        StudyParticipant phoneOnly = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withEmail(null).build();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, phoneOnly, true);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        // The account will not be verified.
        verify(account).setStatus(AccountStatus.UNVERIFIED);
        verify(account).setPhone(PHONE);
        verify(account).setPhoneVerified(Boolean.FALSE);
    }
    
    @Test
    public void createParticipantWithPhoneEmailEnabledVerificationNotWanted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval(null, null);

        StudyParticipant phoneOnly = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withEmail(null).build();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, phoneOnly, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        // The account will not be verified.
        verify(account).setStatus(AccountStatus.ENABLED);
        verify(account).setPhone(PHONE);
        verify(account).setPhoneVerified(Boolean.FALSE);
    }
    
    @Test
    public void getPagedAccountSummaries() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, "foo", "bar", START_DATE, END_DATE);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, "foo", "bar", START_DATE, END_DATE); 
    }
    
    @Test(expected = NullPointerException.class)
    public void getPagedAccountSummariesWithBadStudy() {
        participantService.getPagedAccountSummaries(null, 0, 100, null, null, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithNegativeOffsetBy() {
        participantService.getPagedAccountSummaries(STUDY, -1, 100, null, null, null, null);
    }

    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithNegativePageSize() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100, null, null, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithBadDateRange() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100, null, null, END_DATE, START_DATE);
    }
    
    @Test
    public void getPagedAccountSummariesWithoutEmailOrPhoneFilterOK() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, null, null, null, null);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, null, null, null, null); 
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithTooLargePageSize() {
        participantService.getPagedAccountSummaries(STUDY, 0, 251, null, null, null, null);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        participantService.getParticipant(STUDY, ID, false);
    }
    
    @Test
    public void getStudyParticipant() {
        // A lot of mocks have to be set up first, this call aggregates almost everything we know about the user
        DateTime createdOn = DateTime.now();
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(account.getStudyIdentifier()).thenReturn(STUDY.getStudyIdentifier());
        when(account.getFirstName()).thenReturn(FIRST_NAME);
        when(account.getLastName()).thenReturn(LAST_NAME);
        when(account.getEmail()).thenReturn(EMAIL);
        when(account.getPhone()).thenReturn(PHONE);
        when(account.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(account.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(account.getId()).thenReturn(ID);
        when(account.getStatus()).thenReturn(AccountStatus.DISABLED);
        when(account.getCreatedOn()).thenReturn(createdOn);
        when(account.getAttribute("attr2")).thenReturn("anAttribute2");
        List<ConsentSignature> sigs1 = Lists.newArrayList(new ConsentSignature.Builder()
                .withName("Name 1").withBirthdate("1980-01-01").build());
        when(account.getConsentSignatureHistory(SubpopulationGuid.create("guid1"))).thenReturn(sigs1);
        when(account.getClientData()).thenReturn(TestUtils.getClientData());
        
        mockHealthCodeAndAccountRetrieval();
        
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
        
        when(lookup.getEnum(SHARING_SCOPE, SharingScope.class)).thenReturn(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        when(lookup.getBoolean(EMAIL_NOTIFICATIONS)).thenReturn(true);
        when(lookup.getString(EXTERNAL_IDENTIFIER)).thenReturn(EXTERNAL_ID);
        when(lookup.getStringSet(DATA_GROUPS)).thenReturn(TestUtils.newLinkedHashSet("group1","group2"));
        when(lookup.getOrderedStringSet(LANGUAGES)).thenReturn(USER_LANGUAGES);
        when(lookup.getTimeZone(TIME_ZONE)).thenReturn(USER_TIME_ZONE);
        when(optionsService.getOptions(HEALTH_CODE)).thenReturn(lookup);
        
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
        
        participantService.signUserOut(STUDY, ID);
    }
    
    @Test
    public void signOutUser() {
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(account.getId()).thenReturn("userId");
        
        participantService.signUserOut(STUDY, ID);
        
        verify(accountDao).getAccount(ACCOUNT_ID);
        verify(accountDao).signOut(accountIdCaptor.capture());
        verify(cacheProvider).removeSessionByUserId("userId");
        
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountIdCaptor.getValue().getStudyId());
        assertEquals("userId", accountIdCaptor.getValue().getId());
    }
    
    @Test
    public void updateParticipantWithExternalIdValidationAddingId() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
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

        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn(null).when(lookup).getString(EXTERNAL_IDENTIFIER);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        Map<ParticipantOption, String> options = optionsCaptor.getValue();
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), options.get(SHARING_SCOPE));
        assertEquals("true", options.get(EMAIL_NOTIFICATIONS));
        assertTrue(options.get(DATA_GROUPS).contains("group1"));
        assertTrue(options.get(DATA_GROUPS).contains("group2"));
        assertTrue(options.get(LANGUAGES).contains("de"));
        assertTrue(options.get(LANGUAGES).contains("fr"));
        assertNull(options.get(EXTERNAL_IDENTIFIER)); // can't set this
        assertNull(options.get(TIME_ZONE)); // can't set this
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account).setFirstName(FIRST_NAME);
        verify(account).setLastName(LAST_NAME);
        verify(account).setAttribute("can_be_recontacted", "true");
        verify(account).setClientData(TestUtils.getClientData());
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
        verify(accountDao, never()).updateAccount(any());
        verifyNoMoreInteractions(optionsService);
        verifyNoMoreInteractions(externalIdService);
    }
    
    @Test
    public void updateParticipantNoEmailOrPhoneChange() {
        mockHealthCodeAndAccountRetrieval(); // Account will have same phone/email as participant
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        // And so consequently, none of these are changed.
        verify(account, never()).setEmail(anyString());
        verify(account, never()).setEmailVerified(anyBoolean());
        verify(account, never()).setPhone(any());
        verify(account, never()).setPhoneVerified(anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
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

        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(null).build();

        participantService.updateParticipant(STUDY, EnumSet.of(ADMIN), participant);

        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account, never()).setStatus(any());
    }

    @Test
    public void updateParticipantEmailCannotBeChanged() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval("persisted@email.com", null);
        when(account.getEmailVerified()).thenReturn(Boolean.TRUE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail("updated@email.com").withEmailVerified(Boolean.FALSE).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account, never()).setEmail(anyString());
        verify(account, never()).setEmailVerified(anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
    }

    @Test
    public void updateParticipantEmailCanBeAddedWithVerification() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        
        // Set emailVerified = true to test that this is always set to false, regardless of submitted object
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail("updated@email.com")
                .withEmailVerified(Boolean.TRUE).withEmailVerified(Boolean.FALSE).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account).setEmail("updated@email.com");
        verify(account).setEmailVerified(Boolean.FALSE);
        verify(accountWorkflowService).sendEmailVerificationToken(STUDY, ID, "updated@email.com");
    }

    @Test
    public void updateParticipantEmailCanBeAddedNoVerification() {
        STUDY.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval(null, PHONE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail("updated@email.com")
                .withEmailVerified(Boolean.TRUE).withEmailVerified(Boolean.TRUE).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account).setEmail("updated@email.com");
        verify(account).setEmailVerified(Boolean.FALSE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
    }
    
    @Test
    public void updateParticipantEmailCannotBeDeleted() {
        STUDY.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval(EMAIL, null);
        
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), noEmail);
        
        // You can't delete email, so we are going to completely ignore it
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account, never()).setEmail(anyString());
        verify(account, never()).setEmailVerified(anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
    }
    
    @Test
    public void updateParticipantPhoneCannotBeChanged() {
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE);
        when(account.getPhoneVerified()).thenReturn(Boolean.TRUE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withPhone(OTHER_PHONE).withEmailVerified(Boolean.FALSE).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account, never()).setPhone(any());
        verify(account, never()).setPhoneVerified(anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
    }

    @Test
    public void updateParticipantPhoneCanBeAdded() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withPhone(OTHER_PHONE).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account).setPhone(OTHER_PHONE);
        verify(account).setPhoneVerified(Boolean.FALSE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
    }

    @Test
    public void updateParticipantPhoneCannotBeDeleted() {
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE);
        
        StudyParticipant noPhone = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withPhone(null).build();
        
        participantService.updateParticipant(STUDY, ImmutableSet.of(), noPhone);
        
        // You can't delete email, so we are going to completely ignore it
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account, never()).setPhone(any());
        verify(account, never()).setPhoneVerified(anyBoolean());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(eq(STUDY), eq(ID), anyString());
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
        doReturn(Sets.newHashSet(ADMIN)).when(account).getRoles();
        
        // developer can add the developer role, but they cannot remove the admin role
        verifyRoleUpdate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(ADMIN, DEVELOPER));
    }
    
    @Test
    public void developerCannotDowngradeResearcher() {
        doReturn(Sets.newHashSet(RESEARCHER)).when(account).getRoles();
        
        // developer can add the developer role, but they cannot remove the researcher role
        verifyRoleUpdate(Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void researcherCanDowngradeResearcher() {
        doReturn(Sets.newHashSet(RESEARCHER)).when(account).getRoles();
        
        // researcher can change a researcher to a developer
        verifyRoleUpdate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(DEVELOPER), Sets.newHashSet(DEVELOPER));
    }
    
    @Test
    public void adminCanChangeDeveloperToResearcher() {
        doReturn(Sets.newHashSet(DEVELOPER)).when(account).getRoles();
        
        // admin can convert a developer to a researcher
        verifyRoleUpdate(Sets.newHashSet(ADMIN), Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER));
    }
    
    @Test
    public void adminCanChangeResearcherToAdmin() {
        doReturn(Sets.newHashSet(RESEARCHER)).when(account).getRoles();
        
        // admin can convert a researcher to an admin
        verifyRoleUpdate(Sets.newHashSet(ADMIN), Sets.newHashSet(ADMIN), Sets.newHashSet(ADMIN));
    }
    
    @Test
    public void researcherCanUpgradeDeveloperRole() {
        doReturn(Sets.newHashSet(DEVELOPER)).when(account).getRoles();
        
        // researcher can convert a developer to a researcher
        verifyRoleUpdate(Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER), Sets.newHashSet(RESEARCHER));
    }
    
    @Test
    public void getStudyParticipantWithAccount() throws Exception {
        mockHealthCodeAndAccountRetrieval();
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn(EMAIL).when(account).getEmail();
        doReturn(HEALTH_CODE).when(account).getHealthCode();
        doReturn(TestUtils.getClientData()).when(account).getClientData();
        
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
        
        verify(accountWorkflowService).requestResetPassword(eq(STUDY), eq(ACCOUNT_ID));
    }
    
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
        
        participantService.resendEmailVerification(STUDY, ID);
        
        verify(accountWorkflowService).resendEmailVerificationToken(accountIdCaptor.capture());
        
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(STUDY.getIdentifier(), accountId.getStudyId());
        assertEquals(EMAIL, accountId.getEmail());
    }
    
    @Test
    public void resendConsentAgreement() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.resendConsentAgreement(STUDY, SUBPOP_GUID, ID);
        
        verify(consentService).emailConsentAgreement(eq(STUDY), eq(SUBPOP_GUID), participantCaptor.capture());
        
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
        setupExternalIdTest(true, true);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        
        // Validated and required, use reservation service and don't set as option
        verifyIdReservation(EXTERNAL_ID);
        verifyNotSetAsOption();
    }

    @Test
    public void updateExternalIdValidatedRequiredWithSameValue() {
        setupExternalIdTest(true, true);
        when(lookup.getString(EXTERNAL_IDENTIFIER)).thenReturn(EXTERNAL_ID);
        when(optionsService.getOptions(HEALTH_CODE)).thenReturn(lookup);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        // Submitting same value again with validation does nothing
        verify(externalIdService).assignExternalId(STUDY, EXTERNAL_ID, HEALTH_CODE);
        verifyNotSetAsOption();
    }

    @Test
    public void limitNotExceededException() {
        mockHealthCodeAndAccountRetrieval();
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(9);
        when(accountDao.getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_MINIMUM_PAGE_SIZE, null, null, null, null))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(STUDY,  CALLER_ROLES, PARTICIPANT, false);
    }
    
    @Test
    public void throwLimitExceededExactlyException() {
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(10);
        when(accountDao.getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_MINIMUM_PAGE_SIZE, null, null, null, null))
                .thenReturn(accountSummaries);
        
        try {
            participantService.createParticipant(STUDY,  CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(LimitExceededException e) {
            assertEquals("While study is in evaluation mode, it may not exceed 10 accounts.", e.getMessage());
        }
    }
    
    @Test(expected = LimitExceededException.class)
    public void throwLimitExceededException() {
        STUDY.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(13);
        when(accountDao.getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_MINIMUM_PAGE_SIZE, null, null, null, null))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
    }
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> roles, boolean canSetStatus) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(AccountStatus.ENABLED).build();
        
        participantService.updateParticipant(STUDY, roles, participant);

        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();

        if (canSetStatus) {
            verify(account).setStatus(AccountStatus.ENABLED);
        } else {
            verify(account, never()).setStatus(any());
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(STUDY, callerRoles, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PHONE, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        if (rolesThatAreSet != null) {
            verify(account).setRoles(rolesCaptor.capture());
            assertEquals(rolesThatAreSet, rolesCaptor.getValue());
        } else {
            verify(account, never()).setRoles(any());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet, Set<Roles> expected) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(rolesThatAreSet).build();
        participantService.updateParticipant(STUDY, callerRoles, participant);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        if (expected != null) {
            verify(account).setRoles(rolesCaptor.capture());
            assertEquals(expected, rolesCaptor.getValue());
        } else {
            verify(account, never()).setRoles(any());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> expected) {
        verifyRoleUpdate(callerRoles, Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER), expected);
    }

    private void setupExternalIdTest(boolean withValidation, boolean requiredOnSignUp) {
        STUDY.setExternalIdValidationEnabled(withValidation);
        STUDY.setExternalIdRequiredOnSignup(requiredOnSignUp);
        mockHealthCodeAndAccountRetrieval();
    }
    
    private void verifyIdReservation(String withId) {
        verify(externalIdService).reserveExternalId(studyCaptor.capture(), eq(withId), eq(HEALTH_CODE));
        verify(externalIdService).assignExternalId(studyCaptor.capture(), eq(withId), eq(HEALTH_CODE));
        assertTrue(studyCaptor.getAllValues().get(0).isExternalIdValidationEnabled());
        assertTrue(studyCaptor.getAllValues().get(1).isExternalIdValidationEnabled());
    }
    
    private void verifyNotSetAsOption() {
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        for (Map<ParticipantOption,String> optionsLookup : optionsCaptor.getAllValues()) {
            assertNull(optionsLookup.get(EXTERNAL_IDENTIFIER));
        }
    }
}
