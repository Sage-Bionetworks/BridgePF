package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
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
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
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
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2");
    private static final long CONSENT_PUBLICATION_DATE = DateTime.now().getMillis();
    private static final Study STUDY = new DynamoStudy();
    private static final String PHONE = "phone";
    static {
        STUDY.setIdentifier("test-study");
        STUDY.setHealthCodeExportEnabled(true);
        STUDY.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        STUDY.setDataGroups(STUDY_DATA_GROUPS);
        STUDY.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        STUDY.getUserProfileAttributes().add(PHONE);
    }
    private static final String USERS_HEALTH_CODE = "POWERS";
    private static final String EXTERNAL_ID = "externalId";
    private static final String HEALTH_CODE = "healthCode";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String PASSWORD = "P@ssword1";
    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(RESEARCHER);
    private static final Set<Roles> USER_ROLES = Sets.newHashSet(DEVELOPER);
    private static final LinkedHashSet<String> USER_LANGUAGES = (LinkedHashSet<String>)BridgeUtils.commaListToOrderedSet("de,fr");
    private static final String EMAIL = "email@email.com";
    private static final String ID = "ASDF";
    private static final Map<String,String> ATTRS = new ImmutableMap.Builder<String,String>().put(PHONE,"123456789").build();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(STUDY.getIdentifier());
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder()
            .withFirstName(FIRST_NAME)
            .withLastName(LAST_NAME)
            .withEmail(EMAIL)
            .withId(ID)
            .withPassword(PASSWORD)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
            .withNotifyByEmail(true)
            .withRoles(USER_ROLES)
            .withDataGroups(STUDY_DATA_GROUPS)
            .withAttributes(ATTRS)
            .withLanguages(USER_LANGUAGES)
            .withStatus(AccountStatus.DISABLED)
            .withExternalId(USERS_HEALTH_CODE).build();
            
    private static final StudyParticipant NO_ID_PARTICIPANT = new StudyParticipant.Builder()
            .copyOf(PARTICIPANT)
            .withExternalId(null).build();
    
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
    private ExternalIdService externalIdService;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private Subpopulation subpopulation;
    
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
    
    @Before
    public void before() {
        STUDY.setExternalIdValidationEnabled(false);
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
        participantService.setParticipantOptionsService(optionsService);
        participantService.setSubpopulationService(subpopService);
        participantService.setUserConsent(consentService);
        participantService.setCacheProvider(cacheProvider);
        participantService.setExternalIdService(externalIdService);
        participantService.setScheduledActivityDao(activityDao);
        participantService.setUploadService(uploadService);
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        doReturn(ID).when(account).getId();
        doReturn(account).when(accountDao).constructAccount(STUDY, EMAIL, PASSWORD);
        doReturn(account).when(accountDao).getAccount(STUDY, ID);
        doReturn(HEALTH_CODE).when(account).getHealthCode();
        doReturn(EMAIL).when(account).getEmail();
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
    }
    
    @Test
    public void createParticipant() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        IdentifierHolder idHolder = participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        assertEquals(ID, idHolder.getIdentifier());
        
        verify(externalIdService).reserveExternalId(STUDY, USERS_HEALTH_CODE);
        verify(externalIdService).assignExternalId(STUDY, USERS_HEALTH_CODE, HEALTH_CODE);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PASSWORD);
        // suppress email (true) == sendEmail (false)
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), eq(false));
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        
        Map<ParticipantOption, String> options = optionsCaptor.getValue();
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), options.get(SHARING_SCOPE));
        assertEquals("true", options.get(EMAIL_NOTIFICATIONS));
        // Because strict validation is enabled, we do not update this property along with the others, we
        // go through externalIdService
        assertNull(options.get(EXTERNAL_IDENTIFIER));
        assertTrue(options.get(DATA_GROUPS).contains("group1"));
        assertTrue(options.get(DATA_GROUPS).contains("group2"));
        assertTrue(options.get(LANGUAGES).contains("de"));
        assertTrue(options.get(LANGUAGES).contains("fr"));
        
        Account account = accountCaptor.getValue();
        verify(account).setFirstName(FIRST_NAME);
        verify(account).setLastName(LAST_NAME);
        verify(account).setAttribute(PHONE, "123456789");
        verify(account).setRoles(USER_ROLES);
        // Not called on create
        verify(account, never()).setStatus(AccountStatus.DISABLED);
        
        // don't update cache
        verify(cacheProvider, never()).removeSessionByUserId(ID);
    }
    
    // Or any other failure to reserve an externalId
    @Test
    public void createParticipantWithAssignedId() {
        STUDY.setExternalIdValidationEnabled(true);
        
        doThrow(new EntityAlreadyExistsException(ExternalIdentifier.create(STUDY, "AAA")))
            .when(externalIdService).reserveExternalId(STUDY, USERS_HEALTH_CODE);
        
        try {
            participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
        }
        verify(externalIdService).reserveExternalId(STUDY, USERS_HEALTH_CODE);
        verifyNoMoreInteractions(accountDao);
        verifyNoMoreInteractions(optionsService);
    }
    
    @Test
    public void createParticipantWithExternalIdValidation() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);
        verify(externalIdService).reserveExternalId(STUDY, USERS_HEALTH_CODE);
        // Do not set the externalId with the other options, go through the externalIdService
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        Map<ParticipantOption,String> options = optionsCaptor.getValue();
        assertNull(options.get(EXTERNAL_IDENTIFIER));
        verify(externalIdService).assignExternalId(STUDY, USERS_HEALTH_CODE, HEALTH_CODE);
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
    public void createParticipantWithNoExternalIdValidation() {
        STUDY.setExternalIdValidationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(STUDY, CALLER_ROLES, PARTICIPANT, false);

        verify(externalIdService).reserveExternalId(STUDY, USERS_HEALTH_CODE);
        // set externalId like any other option, we're not using externalIdService
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        Map<ParticipantOption,String> options = optionsCaptor.getValue();
        assertEquals(USERS_HEALTH_CODE, options.get(EXTERNAL_IDENTIFIER));
        verify(externalIdService).assignExternalId(STUDY, USERS_HEALTH_CODE, HEALTH_CODE);
    }
    
    @Test
    public void getPagedAccountSummaries() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, "foo", START_DATE, END_DATE);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, "foo", START_DATE, END_DATE); 
    }
    
    @Test(expected = NullPointerException.class)
    public void getPagedAccountSummariesWithBadStudy() {
        participantService.getPagedAccountSummaries(null, 0, 100, null, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithNegativeOffsetBy() {
        participantService.getPagedAccountSummaries(STUDY, -1, 100, null, null, null);
    }

    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithNegativePageSize() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100, null, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithBadDateRange() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100, null, END_DATE, START_DATE);
    }
    
    @Test
    public void getPagedAccountSummariesWithoutEmailFilterOK() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, null, null, null);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, null, null, null); 
    }
    
    @Test(expected = BadRequestException.class)
    public void getPagedAccountSummariesWithTooLargePageSize() {
        participantService.getPagedAccountSummaries(STUDY, 0, 251, null, null, null);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountDao.getAccount(STUDY, ID)).thenReturn(null);
        
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
        when(account.getId()).thenReturn(ID);
        when(account.getStatus()).thenReturn(AccountStatus.DISABLED);
        when(account.getCreatedOn()).thenReturn(createdOn);
        when(account.getAttribute("attr2")).thenReturn("anAttribute2");
        
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

        List<ConsentSignature> sigs1 = Lists.newArrayList(new ConsentSignature.Builder()
                .withName("Name 1").withBirthdate("1980-01-01").build());
        when(account.getConsentSignatureHistory(SubpopulationGuid.create("guid1"))).thenReturn(sigs1);
        
        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SubpopulationGuid.create("guid1"))).thenReturn(subpop1);
        when(subpopService.getSubpopulation(STUDY.getStudyIdentifier(), SubpopulationGuid.create("guid2"))).thenReturn(subpop2);
        
        when(lookup.getEnum(SHARING_SCOPE, SharingScope.class)).thenReturn(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        when(lookup.getBoolean(EMAIL_NOTIFICATIONS)).thenReturn(true);
        when(lookup.getString(EXTERNAL_IDENTIFIER)).thenReturn(EXTERNAL_ID);
        when(lookup.getStringSet(DATA_GROUPS)).thenReturn(TestUtils.newLinkedHashSet("group1","group2"));
        when(lookup.getOrderedStringSet(LANGUAGES)).thenReturn(USER_LANGUAGES);
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
        assertEquals(ID, participant.getId());
        assertEquals(AccountStatus.DISABLED, participant.getStatus());
        assertEquals(createdOn, participant.getCreatedOn());
        assertEquals(USER_LANGUAGES, participant.getLanguages());
        
        assertNull(participant.getAttributes().get("attr1"));
        assertEquals("anAttribute2", participant.getAttributes().get("attr2"));
        
        List<UserConsentHistory> retrievedHistory1 = participant.getConsentHistories().get(subpop1.getGuidString());
        assertEquals(1, retrievedHistory1.size());
        
        List<UserConsentHistory> retrievedHistory2 = participant.getConsentHistories().get(subpop2.getGuidString());
        assertTrue(retrievedHistory2.isEmpty());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signOutUserWhoDoesNotExist() {
        when(accountDao.getAccount(STUDY, ID)).thenReturn(null);
        
        participantService.signUserOut(STUDY, ID);
    }
    
    @Test
    public void signOutUser() {
        when(accountDao.getAccount(STUDY, ID)).thenReturn(account);
        when(account.getId()).thenReturn("userId");
        
        participantService.signUserOut(STUDY, ID);
        
        verify(accountDao).getAccount(STUDY, ID);
        verify(cacheProvider).removeSessionByUserId("userId");
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
        assertNull(options.get(EXTERNAL_IDENTIFIER));
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        verify(account).setFirstName(FIRST_NAME);
        verify(account).setLastName(LAST_NAME);
        verify(account).setStatus(AccountStatus.DISABLED);
        verify(account).setAttribute(PHONE, "123456789");
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantWithExternalIdValidationChangingId() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn("BBB").when(lookup).getString(EXTERNAL_IDENTIFIER);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
    }

    @Test(expected = BadRequestException.class)
    public void updateParticipantWithExternalIdValidationRemovingId() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn("BBB").when(lookup).getString(EXTERNAL_IDENTIFIER);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, NO_ID_PARTICIPANT);
    }
    
    @Test
    public void updateParticipantWithExternalIdValidationNoIdChange() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn(USERS_HEALTH_CODE).when(lookup).getString(EXTERNAL_IDENTIFIER);
        
        // This just succeeds because the IDs are the same, and we'll verify no attempt was made to update it.
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verifyNoMoreInteractions(externalIdService);
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantWithExternalIdValidationIdMissing() {
        STUDY.setExternalIdValidationEnabled(true);
        mockHealthCodeAndAccountRetrieval();   
        
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        doReturn(null).when(lookup).getString(EXTERNAL_IDENTIFIER);
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, NO_ID_PARTICIPANT);
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
        doThrow(new EntityNotFoundException(Account.class)).when(accountDao).getAccount(STUDY, ID);
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
    public void updateParticipantWithNoExternalIdValidation() {
        STUDY.setExternalIdValidationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.updateParticipant(STUDY, CALLER_ROLES, PARTICIPANT);
        
        verifyNoMoreInteractions(externalIdService);
        verify(optionsService).setAllOptions(eq(STUDY.getStudyIdentifier()), eq(HEALTH_CODE), optionsCaptor.capture());
        Map<ParticipantOption, String> options = optionsCaptor.getValue();
        assertEquals(USERS_HEALTH_CODE, options.get(EXTERNAL_IDENTIFIER));
    }
    
    @Test
    public void createParticipantWithoutExternalIdAndNoValidation() {
        STUDY.setExternalIdValidationEnabled(false);
        mockHealthCodeAndAccountRetrieval();

        // These are the minimal credentials and they should work.
        IdentifierHolder idHolder = participantService.createParticipant(STUDY, CALLER_ROLES, NO_ID_PARTICIPANT, false);
        assertEquals(ID, idHolder.getIdentifier());
        verifyNoMoreInteractions(externalIdService); // no ID, no calls to this service
    }
    
    @Test
    public void userCannotSetStatusOnCreate() {
        verifyStatusCreate(Sets.newHashSet());
    }
    
    @Test
    public void noRoleCanSetStatusOnCreate() {
        verifyStatusCreate(Sets.newHashSet(RESEARCHER, ADMIN, DEVELOPER));
    }
    
    @Test
    public void userCannotChangeStatus() {
        verifyStatusUpdate(Sets.newHashSet(), null);
    }
    
    @Test
    public void developerCanChangeStatusOnEdit() {
        verifyStatusUpdate(Sets.newHashSet(DEVELOPER), AccountStatus.DISABLED);
    }
    
    @Test
    public void researcherCanChangeStatusOnEdit() {
        verifyStatusUpdate(Sets.newHashSet(RESEARCHER), AccountStatus.DISABLED);
    }
    
    @Test
    public void adminCanChangeStatusOnEdit() {
        verifyStatusUpdate(Sets.newHashSet(ADMIN), AccountStatus.DISABLED);
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
        
        StudyParticipant participant = participantService.getParticipant(STUDY, account, false);
        
        // The most important thing here is that participant includes health code
        assertEquals(HEALTH_CODE, participant.getHealthCode());
        // Other fields exist too, but getParticipant() is tested in its entirety earlier in this test.
        assertEquals(EMAIL, participant.getEmail());
        assertEquals(ID, participant.getId());
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
        
        verify(accountDao).requestResetPassword(eq(STUDY), emailCaptor.capture());
        
        Email email = emailCaptor.getValue();
        assertEquals(STUDY.getStudyIdentifier(), email.getStudyIdentifier());
        assertEquals(EMAIL, email.getEmail());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void requestResetPasswordNoUserThrowsCorrectException() {
        participantService.requestResetPassword(STUDY, ID);
    }
    
    @Test
    public void canGetActivityHistoryWithDefaults() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(STUDY, ID, null, null);
        
        verify(activityDao).getActivityHistory(HEALTH_CODE, null, 50);
    }
    
    @Test
    public void canGetActivityHistoryLimitMinPaging() {
        mockHealthCodeAndAccountRetrieval();
        try {
            participantService.getActivityHistory(STUDY, ID, null, 2);    
            fail("Should have thrown an exception");
        } catch(BadRequestException e) {
            assertEquals("pageSize must be from 5-100 records", e.getMessage());
        }
        verifyNoMoreInteractions(activityDao);
    }
    
    @Test
    public void canGetActivityHistoryLimitMaxPaging() {
        mockHealthCodeAndAccountRetrieval();
        try {
            participantService.getActivityHistory(STUDY, ID, null, 102);    
            fail("Should have thrown an exception");
        } catch(BadRequestException e) {
            assertEquals("pageSize must be from 5-100 records", e.getMessage());
        }
        verifyNoMoreInteractions(activityDao);
    }
    
    @Test
    public void canGetActivityHistory() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(STUDY, ID, "key", 30);
        
        verify(activityDao).getActivityHistory(HEALTH_CODE, "key", 30);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityHistoryNoUserThrowsCorrectException() {
        participantService.getActivityHistory(STUDY, ID, null, 40);
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
        
        verify(accountDao).resendEmailVerificationToken(eq(STUDY.getStudyIdentifier()), emailCaptor.capture());
        
        Email email = emailCaptor.getValue();
        assertEquals(STUDY.getStudyIdentifier(), email.getStudyIdentifier());
        assertEquals(EMAIL, email.getEmail());
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
        
        verify(consentService).withdrawAllConsents(STUDY, account, withdrawal, withdrewOn);
    }
    
    @Test
    public void getUploads() {
        mockHealthCodeAndAccountRetrieval();
        DateTime startTime = DateTime.parse("2015-11-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2015-11-01T23:59:59.999Z");
        
        participantService.getUploads(STUDY, ID, startTime, endTime);
        
        verify(uploadService).getUploads(HEALTH_CODE, startTime, endTime);
    }
    
    @Test
    public void getUploadsWithoutDates() {
        // Just verify this throws no exceptions
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getUploads(STUDY, ID, null, null);
        
        verify(uploadService).getUploads(HEALTH_CODE, null, null);
    }
    
    private void verifyStatusCreate(Set<Roles> callerRoles) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(AccountStatus.ENABLED).build();
        
        participantService.createParticipant(STUDY, callerRoles, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), eq(false));
        Account account = accountCaptor.getValue();
        
        verify(account, never()).setStatus(any());
    }
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying, 
    // that it has been set. If the setter is not called, the existing status will be sent back to Stormpath.
    private void verifyStatusUpdate(Set<Roles> roles, AccountStatus status) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStatus(status).build();
        
        participantService.updateParticipant(STUDY, roles, participant);

        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();

        if (status == null) {
            verify(account, never()).setStatus(any());    
        } else {
            verify(account).setStatus(status);
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(STUDY, callerRoles, participant, false);
        
        verify(accountDao).constructAccount(STUDY, EMAIL, PASSWORD);
        verify(accountDao).createAccount(eq(STUDY), accountCaptor.capture(), eq(false));
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
}
