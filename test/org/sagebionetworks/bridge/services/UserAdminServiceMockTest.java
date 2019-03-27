package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class UserAdminServiceMockTest {
    
    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private NotificationsService notificationsService;

    @Mock
    private ParticipantService participantService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private AccountDao accountDao;
    
    @Mock
    private Account account;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private HealthDataService healthDataService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private ScheduledActivityService scheduledActivityService;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    private ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private UserAdminService service;
    
    private Map<SubpopulationGuid,ConsentStatus> statuses;
    
    @Before
    public void before() {
        service = new UserAdminService();
        service.setAuthenticationService(authenticationService);
        service.setConsentService(consentService);
        service.setNotificationsService(notificationsService);
        service.setParticipantService(participantService);
        service.setUploadService(uploadService);
        service.setAccountDao(accountDao);
        service.setCacheProvider(cacheProvider);
        service.setHealthDataService(healthDataService);
        service.setScheduledActivityService(scheduledActivityService);
        service.setActivityEventService(activityEventService);
        service.setExternalIdService(externalIdService);

        // Make a user with multiple consent statuses, and just verify that we call the 
        // consent service that many times.
        statuses = Maps.newHashMap();
        addConsentStatus(statuses, "subpop1");
        addConsentStatus(statuses, "subpop2");
        addConsentStatus(statuses, "subpop3");
        
        UserSession session = new UserSession();
        session.setConsentStatuses(statuses);
        
        when(authenticationService.signIn(any(), any(), any())).thenReturn(session);
        
        doReturn(new IdentifierHolder("ABC")).when(participantService).createParticipant(any(), any(),
                anyBoolean());
        doReturn(new StudyParticipant.Builder().withId("ABC").build()).when(participantService).getParticipant(any(),
                anyString(), anyBoolean());
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    private void addConsentStatus(Map<SubpopulationGuid,ConsentStatus> statuses, String guid) {
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        ConsentStatus status = new ConsentStatus.Builder().withConsented(false).withGuid(subpopGuid).withName(guid)
                .withRequired(true).build();
        statuses.put(subpopGuid, status);
    }
    
    @Test
    public void creatingUserConsentsToAllRequiredConsents() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create("foo1"), TestConstants.REQUIRED_SIGNED_CURRENT);
        statuses.put(SubpopulationGuid.create("foo2"), TestConstants.REQUIRED_SIGNED_OBSOLETE);
        when(consentService.getConsentStatuses(any())).thenReturn(statuses);
        
        service.createUser(study, participant, null, true, true);
        
        verify(participantService).createParticipant(study, participant, false);
        verify(authenticationService).signIn(eq(study), contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(study.getStudyIdentifier(), context.getStudyIdentifier());
        
        verify(consentService).consentToResearch(eq(study), eq(SubpopulationGuid.create("foo1")), any(StudyParticipant.class), any(),
                eq(SharingScope.NO_SHARING), eq(false));
        verify(consentService).consentToResearch(eq(study), eq(SubpopulationGuid.create("foo2")), any(StudyParticipant.class), any(),
                eq(SharingScope.NO_SHARING), eq(false));

        SignIn signIn = signInCaptor.getValue();
        assertEquals(participant.getEmail(), signIn.getEmail());
        assertEquals(participant.getPassword(), signIn.getPassword());
        
        verify(consentService).getConsentStatuses(context);
    }
    
    @Test
    public void creatingUserWithPhone() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword("password").build();

        service.createUser(study, participant, null, true, true);
        
        verify(participantService).createParticipant(study, participant, false);
        verify(authenticationService).signIn(eq(study), contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(study.getStudyIdentifier(), context.getStudyIdentifier());
        
        SignIn signIn = signInCaptor.getValue();
        assertEquals(participant.getPhone(), signIn.getPhone());
        assertEquals(participant.getPassword(), signIn.getPassword());
        
        verify(consentService).getConsentStatuses(context);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void creatingUserWithoutEmailOrPhoneProhibited() {
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withPassword("password").build();

        service.createUser(study, participant, null, true, true);
    }
    
    @Test
    public void creatingUserWithSubpopulationOnlyConsentsToThatSubpopulation() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
                
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        SubpopulationGuid consentedGuid = statuses.keySet().iterator().next();
        
        UserSession session = service.createUser(study, participant, consentedGuid, true, true);
        
        verify(participantService).createParticipant(study, participant, false);
        
        // consented to the indicated subpopulation
        verify(consentService).consentToResearch(eq(study), eq(consentedGuid), any(StudyParticipant.class), any(), eq(SharingScope.NO_SHARING), eq(false));
        // but not to the other two
        for (SubpopulationGuid guid : session.getConsentStatuses().keySet()) {
            if (guid != consentedGuid) {
                verify(consentService, never()).consentToResearch(eq(study), eq(guid), eq(participant), any(), eq(SharingScope.NO_SHARING), eq(false));    
            }
        }
    }
    
    @Test
    public void deleteUser() {
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        
        AccountId accountId = AccountId.forId(study.getIdentifier(),  "userId");

        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "userId");
        as1.setExternalId("subAextId");
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "userId");
        as2.setExternalId("subBextId");
        Set<AccountSubstudy> substudies = ImmutableSet.of(as1, as2);
        
        doReturn("userId").when(account).getId();
        doReturn("healthCode").when(account).getHealthCode();
        doReturn("externalId").when(account).getExternalId();
        doReturn(substudies).when(account).getAccountSubstudies();
        doReturn(account).when(accountDao).getAccount(accountId);
        
        service.deleteUser(study, "userId");
        
        // Verify a lot of stuff is deleted or removed
        verify(cacheProvider).removeSessionByUserId("userId");
        verify(cacheProvider).removeRequestInfo("userId");
        verify(healthDataService).deleteRecordsForHealthCode("healthCode");
        verify(notificationsService).deleteAllRegistrations(study.getStudyIdentifier(), "healthCode");
        verify(uploadService).deleteUploadsForHealthCode("healthCode");
        verify(scheduledActivityService).deleteActivitiesForUser("healthCode");
        verify(activityEventService).deleteActivityEvents("healthCode");
        verify(externalIdService).unassignExternalId(accountCaptor.capture(), eq("externalId"));
        verify(externalIdService).unassignExternalId(accountCaptor.capture(), eq("subAextId"));
        verify(externalIdService).unassignExternalId(accountCaptor.capture(), eq("subBextId"));
        verify(accountDao).deleteAccount(accountId);
        
        assertEquals("healthCode", accountCaptor.getValue().getHealthCode());
    }
    
}
