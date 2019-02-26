package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.createJson;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {

    private static final String SUBPOP_GUID = "subpopGuid";

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };
    
    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};
    
    private static final TypeReference<ForwardCursorPagedResourceList<? extends Upload>> UPLOADS_REF = new TypeReference<ForwardCursorPagedResourceList<? extends Upload>>(){};
    
    private static final Set<Roles> CALLER_ROLES = ImmutableSet.of(Roles.RESEARCHER);
    
    private static final Set<String> CALLER_SUBS = ImmutableSet.of("substudyA");
    
    private static final String ID = "ASDF";
    
    private static final AccountId ACCOUNT_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, "ASDF");

    private static final String EMAIL = "email@email.com";
    
    private static final String ACTIVITY_GUID = "activityGuid";

    private static final DateTime START_TIME = DateTime.now().minusHours(3);
    
    private static final DateTime END_TIME = DateTime.now();
    
    private static final Set<String> EMPTY_SET = new HashSet<>();
    
    private static final AccountSummary SUMMARY = new AccountSummary("firstName", "lastName", "email",
            TestConstants.PHONE, null, ImmutableMap.of("substudyA", "externalId"), "id", DateTime.now(),
            AccountStatus.ENABLED, TestConstants.TEST_STUDY, ImmutableSet.of());

    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withPhone(TestConstants.PHONE).withPassword(TestConstants.PASSWORD).build();
    private static final IdentifierUpdate PHONE_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            TestConstants.PHONE, null);
    private static final IdentifierUpdate EMAIL_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST,
            TestConstants.EMAIL, null, null);
    private static final IdentifierUpdate EXTID_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST,
            null, null, "some-new-extid");
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ConsentService mockConsentService;
    
    @Mock
    private ParticipantService mockParticipantService;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AuthenticationService authService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private UserAdminService userAdminService;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> startTimeCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> endTimeCaptor;
    
    @Captor
    private ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> startsOnCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> endsOnCaptor;

    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    private ArgumentCaptor<IdentifierUpdate> identifierUpdateCaptor;
    
    @Captor
    private ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    private ArgumentCaptor<SmsTemplate> templateCaptor;
    
    private UserSession session;
    
    private Study study;
    
    private StudyParticipant participant;

    @Before
    public void before() throws Exception {
        study = new DynamoStudy();
        study.setUserProfileAttributes(Sets.newHashSet("foo","baz"));
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        participant = new StudyParticipant.Builder()
                .withRoles(CALLER_ROLES)
                .withSubstudyIds(CALLER_SUBS)
                .withId(ID).build();
        
        session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        session.setParticipant(participant);
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(summaries, 30)
                .withRequestParam("offsetBy", 10)
                .withRequestParam("pageSize", 20)
                .withRequestParam("startTime", START_TIME)
                .withRequestParam("endTime", END_TIME)
                .withRequestParam("emailFilter", "foo");
        
        when(mockParticipantService.getPagedAccountSummaries(eq(study), any())).thenReturn(page);
        
        controller.setParticipantService(mockParticipantService);
        controller.setStudyService(mockStudyService);
        controller.setAuthenticationService(authService);
        controller.setCacheProvider(mockCacheProvider);
        controller.setUserAdminService(userAdminService);

        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        sessionUpdateService.setConsentService(mockConsentService);
        sessionUpdateService.setNotificationTopicService(mock(NotificationTopicService.class));

        controller.setSessionUpdateService(sessionUpdateService);
        
        TestUtils.mockPlay().mock();
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }

    @Test
    public void createSmsNotificationRegistration() throws Exception {
        // Requires researcher role.
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(CALLER_ROLES).build());

        // Execute.
        Result result = controller.createSmsRegistration(ID);
        assertResult(result, 201, "SMS notification registration created");

        // Verify dependent services.
        verify(mockParticipantService).createSmsRegistration(study, ID);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipants() throws Exception {
        Result result = controller.getParticipants("10", "20", "emailSubstring", "phoneSubstring",
                START_TIME.toString(), END_TIME.toString(), null, null);
        
        verifyPagedResourceListParameters(result);
        
        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(10, search.getOffsetBy());
        assertEquals(20, search.getPageSize());
        assertEquals("emailSubstring", search.getEmailFilter());
        assertEquals("phoneSubstring", search.getPhoneFilter());
        assertEquals(EMPTY_SET, search.getAllOfGroups());
        assertEquals(EMPTY_SET, search.getNoneOfGroups());
        assertEquals(START_TIME.toString(), search.getStartTime().toString());
        assertEquals(END_TIME.toString(), search.getEndTime().toString());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsWithStartTimeEndTime() throws Exception {
        Result result = controller.getParticipants("10", "20", "emailSubstring", "phoneSubstring", null, null,
                START_TIME.toString(), END_TIME.toString());
        
        verifyPagedResourceListParameters(result);
        
        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(10, search.getOffsetBy());
        assertEquals(20, search.getPageSize());
        assertEquals("emailSubstring", search.getEmailFilter());
        assertEquals("phoneSubstring", search.getPhoneFilter());
        assertEquals(EMPTY_SET, search.getAllOfGroups());
        assertEquals(EMPTY_SET, search.getNoneOfGroups());
        assertEquals(START_TIME.toString(), search.getStartTime().toString());
        assertEquals(END_TIME.toString(), search.getEndTime().toString());
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null, null, null, null, null, null);
    }

    @Test
    public void getParticipant() throws Exception {
        study.setHealthCodeExportEnabled(true);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE).build();
        
        when(mockParticipantService.getParticipant(study, ACCOUNT_ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID, true);
        TestUtils.assertResult(result, 200);
        
        // StudyParticipant will encrypt the healthCode when you ask for it, so validate the
        // JSON itself.
        JsonNode node = TestUtils.getJson(result);
        assertTrue(node.has("firstName"));
        assertTrue(node.has("healthCode"));
        assertFalse(node.has("encryptedHealthCode"));
        
        verify(mockParticipantService).getParticipant(study, ACCOUNT_ID, true);
    }
    
    @Test
    public void getParticipantWithNoHealthCode() throws Exception {
        study.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(study, ACCOUNT_ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID, true);
        TestUtils.assertResult(result, 200);
        
        String json = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);
        
        assertEquals("Test", retrievedParticipant.getFirstName());
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test
    public void signUserOut() throws Exception {
        Result result = controller.signOut(ID, false);
        TestUtils.assertResult(result, 200, "User signed out.");

        verify(mockParticipantService).signUserOut(study, ID, false);
    }

    @Test
    public void updateParticipant() throws Exception {
        study.getUserProfileAttributes().add("can_be_recontacted");
        TestUtils.mockPlay().withJsonBody(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'can_be_recontacted':'true'},"+
                "'languages':['en','fr']}")).mock();
        
        Result result = controller.updateParticipant(ID);
        assertResult(result, 200, "Participant updated.");
        
        // Both the caller roles and the caller's substudies are passed to participantService
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(ID, participant.getId());
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("true", participant.getAttributes().get("can_be_recontacted"));
        assertEquals(ImmutableList.of("en","fr"), participant.getLanguages());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null, null, null, null, null, null);

        // paging with defaults
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(0, search.getOffsetBy());
        assertEquals(API_DEFAULT_PAGE_SIZE, search.getPageSize());
        assertNull(search.getEmailFilter());
        assertNull(search.getPhoneFilter());
        assertEquals(EMPTY_SET, search.getAllOfGroups());
        assertEquals(EMPTY_SET, search.getNoneOfGroups());
        assertNull(search.getStartTime());
        assertNull(search.getEndTime());
    }
    
    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = setUpCreateParticipant();
        doReturn(holder).when(mockParticipantService).createParticipant(eq(study), any(StudyParticipant.class), eq(true));
        
        Result result = controller.createParticipant();

        assertResult(result, 201);
        String id = MAPPER.readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(mockParticipantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(ImmutableList.of("en","fr"), participant.getLanguages());
    }

    @Test
    public void getParticipantRequestInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        
        doReturn(requestInfo).when(mockCacheProvider).getRequestInfo("userId");
        Result result = controller.getRequestInfo("userId");
        assertResult(result, 200);
        
        // serialization was tested separately... just validate the object is there
        RequestInfo info = MAPPER.readValue(Helpers.contentAsString(result), RequestInfo.class);
        assertEquals(requestInfo, info);
    }
    
    @Test
    public void getParticipantRequestInfoIsNullsafe() throws Exception {
        // There is no request info.
        Result result = controller.getRequestInfo("userId");
        
        assertResult(result, 200);
        RequestInfo info = MAPPER.readValue(Helpers.contentAsString(result), RequestInfo.class);
        assertNotNull(info); // values are all null, but object is returned
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantRequestInfoOnlyReturnsCurrentStudyInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(new StudyIdentifierImpl("some-other-study")).build();
        
        doReturn(requestInfo).when(mockCacheProvider).getRequestInfo("userId");
        controller.getRequestInfo("userId");
    }
    
    private IdentifierHolder setUpCreateParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder("ABCD");
        
        study.getUserProfileAttributes().add("phone");
        TestUtils.mockPlay().withJsonBody(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},"+
                "'languages':['en','fr']}")).mock();
        return holder;
    }

    @Test
    public void updateParticipantWithMismatchedIdsUsesURL() throws Exception {
        TestUtils.mockPlay().withJsonBody(createJson("{'id':'id2'}")).mock();
        
        Result result = controller.updateParticipant("id1");
        assertResult(result, 200, "Participant updated.");
        
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        
        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals("id1", persisted.getId());
    }
    
    @Test
    public void getSelfParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder()
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withFirstName("Test").build();
        
        when(mockParticipantService.getParticipant(study, ID, false)).thenReturn(studyParticipant);

        Result result = controller.getSelfParticipant();
        assertResult(result, 200);
        
        verify(mockParticipantService).getParticipant(study, ID, false);
        
        StudyParticipant deserParticipant = MAPPER.readValue(Helpers.contentAsString(result), StudyParticipant.class);

        assertEquals("Test", deserParticipant.getFirstName());
        assertNull(deserParticipant.getHealthCode());
        assertNull(deserParticipant.getEncryptedHealthCode());
    }
    
    @Test
    public void updateSelfParticipant() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(
                ImmutableSet.of("substudyA", "substudyB")).build());
        
        // All values should be copied over here, also add a healthCode to verify it is not unset.
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(TestUtils.getStudyParticipant(ParticipantControllerTest.class))
                .withId(ID)
                .withLanguages(TestConstants.LANGUAGES)
                .withRoles(ImmutableSet.of(Roles.DEVELOPER)) // <-- should not be passed along
                .withDataGroups(TestConstants.USER_DATA_GROUPS)
                .withSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
                .withHealthCode("healthCode").build();
        session.setParticipant(participant);
        
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        
        String json = MAPPER.writeValueAsString(participant);
        TestUtils.mockPlay().withJsonBody(json).withMockResponse().mock();

        Result result = controller.updateSelfParticipant();
        
        assertResult(result, 200);
        JsonNode node = TestUtils.getJson(result);
        assertEquals("UserSessionInfo", node.get("type").asText());
        assertNull(node.get("healthCode"));
        
        // verify the object is passed to service, one field is sufficient
        verify(mockCacheProvider).setUserSession(any());
        // No roles are passed in this method, and the substudies of the user are passed 
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());

        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("FirstName", captured.getFirstName());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, captured.getSharingScope());
        assertTrue(captured.isNotifyByEmail());
        assertEquals(TestConstants.USER_DATA_GROUPS, captured.getDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, captured.getSubstudyIds());
        assertEquals("true", captured.getAttributes().get("can_be_recontacted"));
        
        verify(mockConsentService).getConsentStatuses(contextCaptor.capture());
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY, context.getStudyIdentifier());
        assertEquals("healthCode", context.getHealthCode());
        assertEquals(ID, context.getUserId());
        assertEquals(ClientInfo.UNKNOWN_CLIENT, context.getClientInfo());
        assertEquals(null, context.getIpAddress());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, context.getUserSubstudyIds());
        assertEquals(TestConstants.LANGUAGES, context.getLanguages());
    }
    
    // Some values will be missing in the JSON and should be preserved from this original participant object.
    // This allows client to provide JSON that's less than the entire participant.
    @Test
    public void partialUpdateSelfParticipant() throws Exception {
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("foo", "bar");
        attrs.put("baz", "bap");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("email@email.com")
                .withId("id")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1","group2"))
                .withAttributes(attrs)
                .withLanguages(ImmutableList.of("en"))
                .withStatus(AccountStatus.DISABLED)
                .withExternalId("POWERS").build();
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        
        TestUtils.mockPlay().withJsonBody(createJson("{'externalId':'simpleStringChange',"+
                "'sharingScope':'no_sharing',"+
                "'notifyByEmail':false,"+
                "'attributes':{'baz':'belgium'},"+
                "'languages':['fr'],"+
                "'status':'enabled',"+
                "'roles':['admin']}")).withMockResponse().mock();
        
        Result result = controller.updateSelfParticipant();
        assertResult(result, 200);
        JsonNode node = TestUtils.getJson(result);
        assertEquals("UserSessionInfo", node.get("type").asText());

        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("firstName", captured.getFirstName());
        assertEquals("lastName", captured.getLastName());
        assertEquals("email@email.com", captured.getEmail());
        assertEquals("password", captured.getPassword());
        assertEquals(SharingScope.NO_SHARING, captured.getSharingScope());
        assertFalse(captured.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), captured.getDataGroups());
        assertNull(captured.getAttributes().get("foo"));
        assertEquals("belgium", captured.getAttributes().get("baz"));
        assertEquals(AccountStatus.ENABLED, captured.getStatus());
        assertEquals(ImmutableList.of("fr"), captured.getLanguages());
        assertEquals("simpleStringChange", captured.getExternalId());
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        Result result = controller.requestResetPassword(ID);
        assertResult(result, 200, "Request to reset password sent to user.");
        
        verify(mockParticipantService).requestResetPassword(study, ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotResetPasswordIfNotResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        session.setParticipant(participant);
        
        controller.requestResetPassword(ID);
    }
    
    @Test
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(ID).build();
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        
        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();
        String json = MAPPER.writeValueAsString(participant);
        
        TestUtils.mockPlay().withJsonBody(json).withMockResponse().mock();

        Result result = controller.updateSelfParticipant();
        assertResult(result, 200);
        
        JsonNode node = TestUtils.getJson(result);
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        // verify the object is passed to service, one field is sufficient
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
    }
    
    @Test
    public void canGetActivityHistoryV2() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(study), eq(ID),
                eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));
        
        Result result = controller.getActivityHistoryV2(ID, ACTIVITY_GUID, START_TIME.toString(), END_TIME.toString(),
                "200", null, "77");
        assertResult(result, 200);
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());

        JsonNode node = MAPPER.readTree(Helpers.contentAsString(result));
        assertEquals("200", node.get("offsetBy").asText());
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(77, page.getRequestParams().get("pageSize"));
        assertEquals("200", page.getRequestParams().get("offsetKey"));
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }
    
    @Test
    public void canGetActivityHistoryV2WithOffsetKey() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(study), eq(ID),
                eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));
        
        Result result = controller.getActivityHistoryV2(ID, ACTIVITY_GUID, START_TIME.toString(), END_TIME.toString(),
                null, "200", "77");
        assertResult(result, 200);
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());

        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(77, page.getRequestParams().get("pageSize"));
        assertEquals("200", page.getRequestParams().get("offsetKey"));
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityV2WithNullValues() throws Exception {
        doReturn(createActivityResultsV2(null, API_DEFAULT_PAGE_SIZE)).when(mockParticipantService).getActivityHistory(
                eq(study), eq(ID), eq(ACTIVITY_GUID), any(), any(), eq(null), eq(API_DEFAULT_PAGE_SIZE));
        
        Result result = controller.getActivityHistoryV2(ID, ACTIVITY_GUID, null, null, null, null, null);
        assertResult(result, 200);
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(API_DEFAULT_PAGE_SIZE, page.getRequestParams().get("pageSize"));
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID), eq(null), eq(null),
                eq(null), eq(API_DEFAULT_PAGE_SIZE));
    }

    @Test
    public void deleteActivities() throws Exception {
        Result result = controller.deleteActivities(ID);
        assertResult(result, 200, "Scheduled activities deleted.");
        
        verify(mockParticipantService).deleteActivities(study, ID);
    }

    @Test
    public void resendEmailVerification() throws Exception {
        Result result = controller.resendEmailVerification(ID);
        assertResult(result, 200, "Email verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(study, ChannelType.EMAIL, ID);
    }
    
    @Test
    public void resendPhoneVerification() throws Exception {
        Result result = controller.resendPhoneVerification(ID);
        assertResult(result, 200, "Phone verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(study, ChannelType.PHONE, ID);
    }
    
    @Test
    public void resendConsentAgreement() throws Exception {
        Result result = controller.resendConsentAgreement(ID, SUBPOP_GUID);
        assertResult(result, 200, "Consent agreement resent to user.");
        
        verify(mockParticipantService).resendConsentAgreement(study, SubpopulationGuid.create(SUBPOP_GUID), ID);
    }

    @Test
    public void withdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            TestUtils.mockPlay().withJsonBody(json).mock();
            
            controller.withdrawFromStudy(ID);
            
            verify(mockParticipantService).withdrawFromStudy(study, ID, new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void withdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            TestUtils.mockPlay().withJsonBody(json).mock();
            
            controller.withdrawConsent(ID, SUBPOP_GUID);
            
            verify(mockParticipantService).withdrawConsent(study, ID, SubpopulationGuid.create(SUBPOP_GUID),
                    new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void getUploads() throws Exception {
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z").withZone(DateTimeZone.UTC);
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z").withZone(DateTimeZone.UTC);

        List<? extends Upload> list = Lists.newArrayList();

        ForwardCursorPagedResourceList<? extends Upload> uploads = new ForwardCursorPagedResourceList<>(list, "abc")
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockParticipantService).getUploads(study, ID, startTime, endTime, 10, "abc");
        
        Result result = controller.getUploads(ID, startTime.toString(), endTime.toString(), 10, "abc");
        assertResult(result, 200);
        
        verify(mockParticipantService).getUploads(study, ID, startTime, endTime, 10, "abc");
        
        // in other words, it's the object we mocked out from the service, we were returned the value.
        ForwardCursorPagedResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertEquals(startTime.toString(), retrieved.getRequestParams().get("startTime"));
        assertEquals(endTime.toString(), retrieved.getRequestParams().get("endTime"));
    }
    
    @Test
    public void getUploadsNullsDateRange() throws Exception {
        List<Upload> list = Lists.newArrayList();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE);
        doReturn(uploads).when(mockParticipantService).getUploads(study, ID, null, null, null, null);
        
        Result result = controller.getUploads(ID, null, null, null, null);
        assertResult(result, 200);
        
        verify(mockParticipantService).getUploads(study, ID, null, null, null, null);
    }
    
    @Test
    public void getNotificationRegistrations() throws Exception {
        List<NotificationRegistration> list = Lists.newArrayList();
        doReturn(list).when(mockParticipantService).listRegistrations(study, ID);
        
        Result result = controller.getNotificationRegistrations(ID);
        assertResult(result, 200);
        JsonNode node = TestUtils.getJson(result);
        assertEquals(0, node.get("items").size());
        assertEquals("ResourceList", node.get("type").asText());
        
        verify(mockParticipantService).listRegistrations(study, ID);
    }
    
    @Test
    public void sendMessage() throws Exception {
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        TestUtils.mockPlay().withBody(message).mock();
        Result result = controller.sendNotification(ID);
        
        assertResult(result, 202, "Message has been sent to external notification service.");
        
        verify(mockParticipantService).sendNotification(eq(study), eq(ID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        
        assertEquals("a subject", captured.getSubject());
        assertEquals("a message", captured.getMessage());
    }
    
    @Test
    public void sendMessageWithSomeErrors() throws Exception {
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        Set<String> erroredRegistrations = ImmutableSet.of("123", "456");
        when(mockParticipantService.sendNotification(study, ID, message)).thenReturn(erroredRegistrations);
        
        TestUtils.mockPlay().withBody(message).mock();
        Result result = controller.sendNotification(ID);
        
        assertResult(result, 202, "Message has been sent to external notification service. Some registrations returned errors: 123, 456.");
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnauthorizedException.class)
    public void getParticipantsForWorkerOnly() throws Exception {
        DateTime start = DateTime.now();
        DateTime end = DateTime.now();
        
        controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring", "phoneSubstring",
                start.toString(), end.toString(), null, null);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getParticipantForWorkerOnly() throws Exception {
        controller.getParticipantForWorker(study.getIdentifier(), ID, true);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        Result result = controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", START_TIME.toString(), END_TIME.toString(), null, null);

        verifyPagedResourceListParameters(result);
        
        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(10, search.getOffsetBy());
        assertEquals(20, search.getPageSize());
        assertEquals("emailSubstring", search.getEmailFilter());
        assertEquals("phoneSubstring", search.getPhoneFilter());
        assertEquals(EMPTY_SET, search.getAllOfGroups());
        assertEquals(EMPTY_SET, search.getNoneOfGroups());
        assertEquals(START_TIME.toString(), search.getStartTime().toString());
        assertEquals(END_TIME.toString(), search.getEndTime().toString());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsForWorkerUsingStartTimeEndTime() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        Result result = controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", null, null, START_TIME.toString(), END_TIME.toString());
        
        verifyPagedResourceListParameters(result);
        
        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(10, search.getOffsetBy());
        assertEquals(20, search.getPageSize());
        assertEquals("emailSubstring", search.getEmailFilter());
        assertEquals("phoneSubstring", search.getPhoneFilter());
        assertEquals(EMPTY_SET, search.getAllOfGroups());
        assertEquals(EMPTY_SET, search.getNoneOfGroups());
        assertEquals(START_TIME.toString(), search.getStartTime().toString());
        assertEquals(END_TIME.toString(), search.getEndTime().toString());
    }
    
    @Test
    public void getParticipantForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(ID).withHealthCode("healthCode")
                .build();
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, ACCOUNT_ID, true)).thenReturn(foundParticipant);
        
        Result result = controller.getParticipantForWorker(study.getIdentifier(), ID, true);
        assertResult(result, 200);

        JsonNode participantNode = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("healthCode", participantNode.get("healthCode").textValue());
        assertNull(participantNode.get("encryptedHealthCode"));
        assertEquals(ID, participantNode.get("id").textValue());
        
        verify(mockParticipantService).getParticipant(study, ACCOUNT_ID, true);
    }
    
    @Test
    public void getActivityHistoryV3() throws Exception {
        doReturn(createActivityResultsV2("offsetKey", 15)).when(mockParticipantService).getActivityHistory(eq(study),
                eq(ID), eq(ActivityType.SURVEY), eq("referentGuid"), any(), any(), eq("offsetKey"), eq(15));
        
        Result result = controller.getActivityHistoryV3(ID, "surveys", "referentGuid", START_TIME.toString(), END_TIME.toString(),
                "offsetKey", "15");
        assertResult(result, 200);

        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(15, node.get("requestParams").get("pageSize").intValue());
        assertEquals("offsetKey", node.get("requestParams").get("offsetKey").asText());
        
        // The fact this can be converted to a forward cursor object is ideal
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        assertEquals(15, (int)page.getRequestParams().get("pageSize"));
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ActivityType.SURVEY),
                eq("referentGuid"), startTimeCaptor.capture(), endTimeCaptor.capture(), eq("offsetKey"), eq(15));
        assertEquals(START_TIME.toString(), startTimeCaptor.getValue().toString());
        assertEquals(END_TIME.toString(), endTimeCaptor.getValue().toString());
    }
    
    @Test
    public void getActivityHistoryV3DefaultsToNulls() throws Exception {
        Result result = controller.getActivityHistoryV3(ID, "badtypes", null, null, null, null, null);
        assertResult(result, 200);
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(null),
                eq(null), startTimeCaptor.capture(), endTimeCaptor.capture(), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        assertNull(startTimeCaptor.getValue());
        assertNull(endTimeCaptor.getValue());
    }
    
    
    @Test
    public void updateIdentifiersWithPhone() throws Exception {
        TestUtils.mockPlay().withBody(PHONE_UPDATE).withMockResponse().mock();
        
        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);
        
        Result result = controller.updateIdentifiers();
        assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(ID, node.get("id").textValue());
        
        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(), identifierUpdateCaptor.capture());
        verify(mockCacheProvider).setUserSession(sessionCaptor.capture());
        assertEquals(participant.getId(), sessionCaptor.getValue().getId());
        
        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail(), update.getSignIn().getEmail());
        assertEquals(EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword(), update.getSignIn().getPassword());
        assertEquals(TestConstants.PHONE, update.getPhoneUpdate());
        assertNull(update.getEmailUpdate());
    }

    @Test
    public void updateIdentifiersWithEmail() throws Exception {
        TestUtils.mockPlay().withBody(EMAIL_UPDATE).withMockResponse().mock();
        
        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);
        
        Result result = controller.updateIdentifiers();
        assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(ID, node.get("id").textValue());
        
        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(), identifierUpdateCaptor.capture());
        
        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone(), update.getSignIn().getPhone());
        assertEquals(PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword(), update.getSignIn().getPassword());
        assertEquals(TestConstants.EMAIL, update.getEmailUpdate());
        assertNull(update.getPhoneUpdate());
    }

    @Test
    public void updateIdentifiersWithExternalId() throws Exception {
        TestUtils.mockPlay().withBody(EXTID_UPDATE).withMockResponse().mock();
        
        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);
        
        Result result = controller.updateIdentifiers();
        assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(ID, node.get("id").textValue());
        
        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(), identifierUpdateCaptor.capture());
        
        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone(), update.getSignIn().getPhone());
        assertEquals(PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword(), update.getSignIn().getPassword());
        assertEquals("some-new-extid", update.getExternalIdUpdate());
        assertNull(update.getPhoneUpdate());
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void updateIdentifierRequiresAuthentication() throws Exception {
        doReturn(null).when(controller).getSessionIfItExists();
        TestUtils.mockPlay().withBody(PHONE_UPDATE).mock();
        
        controller.updateIdentifiers();
    }
    
    @Test
    public void getParticipantByHealthCode() throws Exception {
        AccountId accountId = AccountId.forHealthCode(study.getIdentifier(), "hc");
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        when(mockParticipantService.getParticipant(study, accountId, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant("healthCode:hc", true);
        TestUtils.assertResult(result, 200);
        
        String json = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);
        
        assertEquals("Test", retrievedParticipant.getFirstName());
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test
    public void getParticipantByExternalId() throws Exception { 
        AccountId accountId = AccountId.forExternalId(study.getIdentifier(), "extid");
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        when(mockParticipantService.getParticipant(study, accountId, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant("externalId:extid", true);
        TestUtils.assertResult(result, 200);
        
        String json = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);
        
        assertEquals("Test", retrievedParticipant.getFirstName());
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test
    public void getParticipantWithNoConsents() throws Exception {
        AccountId accountId = AccountId.forId(study.getIdentifier(), ID);
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        when(mockParticipantService.getParticipant(study, accountId, false)).thenReturn(studyParticipant);
        
        controller.getParticipant(ID, false);
        
        verify(mockParticipantService).getParticipant(study, accountId, false);
    }
    
    @Test
    public void getParticipantForWorkerByHealthCode() throws Exception { 
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        AccountId accountId = AccountId.forHealthCode(study.getIdentifier(), "hc");
        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(ID).build();
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, accountId, true)).thenReturn(foundParticipant);
        
        Result result = controller.getParticipantForWorker(study.getIdentifier(), "healthCode:hc", true);
        assertResult(result, 200);

        JsonNode participantNode = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(ID, participantNode.get("id").textValue());
    }
    
    @Test
    public void getParticipantForWorkerByExternalId() throws Exception { 
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        AccountId accountId = AccountId.forExternalId(study.getIdentifier(), "extid");
        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(ID).build();
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, accountId, true)).thenReturn(foundParticipant);
        
        // all lowercase works key prefix works
        Result result = controller.getParticipantForWorker(study.getIdentifier(), "externalid:extid", true); 
        assertResult(result, 200);

        JsonNode participantNode = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(ID, participantNode.get("id").textValue());
    }
    
    @Test
    public void getParticipantForWorkerNoConsents() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), ID);
        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(ID).build();
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, accountId, false)).thenReturn(foundParticipant);
        
        controller.getParticipantForWorker(study.getIdentifier(), ID, false); 
        
        verify(mockParticipantService).getParticipant(study, accountId, false);
    }
    
    @Test
    public void sendSMS() throws Exception {
        TestUtils.mockPlay().withBody(new SmsTemplate("This is a message")).mock();
        
        Result result = controller.sendSmsMessage(ID);
        
        TestUtils.assertResult(result, 202, "Message sent.");
        verify(mockParticipantService).sendSmsMessage(eq(study), eq(ID), templateCaptor.capture());
        
        SmsTemplate resultTemplate = templateCaptor.getValue();
        assertEquals("This is a message", resultTemplate.getMessage());
    }
    
    @Test
    public void sendSMSForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        TestUtils.mockPlay().withBody(new SmsTemplate("This is a message")).mock();
        
        Result result = controller.sendSmsMessageForWorker(TestConstants.TEST_STUDY_IDENTIFIER, ID);
        
        TestUtils.assertResult(result, 202, "Message sent.");
        verify(mockParticipantService).sendSmsMessage(eq(study), eq(ID), templateCaptor.capture());
        
        SmsTemplate resultTemplate = templateCaptor.getValue();
        assertEquals("This is a message", resultTemplate.getMessage());
    }
    
    @Test
    public void getActivityEventsForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        DynamoActivityEvent anEvent = new DynamoActivityEvent();
        anEvent.setEventId("event-id");
        List<ActivityEvent> events = Lists.newArrayList(anEvent);
        when(mockParticipantService.getActivityEvents(study, ID)).thenReturn(events);
        
        Result result = controller.getActivityEventsForWorker(TestConstants.TEST_STUDY_IDENTIFIER, ID);
        
        verify(mockParticipantService).getActivityEvents(study, ID);
        ResourceList<ActivityEvent> retrievedEvents = TestUtils.getResponsePayload(result, new TypeReference<ResourceList<ActivityEvent>>() {});
        assertEquals("event-id", retrievedEvents.getItems().get(0).getEventId());
    }
    
    @Test
    public void getActivityHistoryForWorkerV2() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        ForwardCursorPagedResourceList<ScheduledActivity> cursor = 
                new ForwardCursorPagedResourceList<>(Lists.newArrayList(ScheduledActivity.create()), "asdf");
        when(mockParticipantService.getActivityHistory(eq(study), eq(ID), eq("activityGuid"), any(), any(), eq("asdf"),
                eq(50))).thenReturn(cursor);
        
        Result result = controller.getActivityHistoryForWorkerV2(TestConstants.TEST_STUDY_IDENTIFIER, ID,
                "activityGuid", START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq("activityGuid"), any(), any(),
                eq("asdf"), eq(50));
        
        ForwardCursorPagedResourceList<ScheduledActivity> retrieved = TestUtils.getResponsePayload(result,
            new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {});
        assertFalse(retrieved.getItems().isEmpty());
    }
    
    @Test
    public void getActivityHistoryForWorkerV3() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        ForwardCursorPagedResourceList<ScheduledActivity> cursor = 
                new ForwardCursorPagedResourceList<>(Lists.newArrayList(ScheduledActivity.create()), "asdf");
        when(mockParticipantService.getActivityHistory(eq(study), eq(ID), eq(ActivityType.TASK), any(), any(), any(),
                eq("asdf"), eq(50))).thenReturn(cursor);
        
        Result result = controller.getActivityHistoryForWorkerV3(TestConstants.TEST_STUDY_IDENTIFIER, ID,
                "tasks", START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ActivityType.TASK), any(), any(),
                any(), eq("asdf"), eq(50));
        
        ForwardCursorPagedResourceList<ScheduledActivity> retrieved = TestUtils.getResponsePayload(result,
            new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {});
        assertFalse(retrieved.getItems().isEmpty());
    }
    
    @Test
    public void deleteTestUserWorks() {
        participant = new StudyParticipant.Builder().copyOf(participant).withDataGroups(Sets.newHashSet("test_user"))
                .build();
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), ID);
        when(mockParticipantService.getParticipant(study, accountId, false)).thenReturn(participant);
        controller.deleteTestParticipant(ID);
        
        verify(userAdminService).deleteUser(study, ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void deleteTestUserNotAResearcher() {
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        session.setParticipant(participant);
        
        controller.deleteTestParticipant(ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void deleteTestUserNotATestAccount() {
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withDataGroups(Sets.newHashSet()).build();
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), ID);
        when(mockParticipantService.getParticipant(study, accountId, false)).thenReturn(participant);
        controller.deleteTestParticipant(ID);
    }
    
    @SuppressWarnings("deprecation")
    private <T> void verifyPagedResourceListParameters(Result result) throws Exception {
        assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(START_TIME.toString(), node.get("startTime").asText());
        assertEquals(END_TIME.toString(), node.get("endTime").asText());
        assertEquals(START_TIME.toString(), node.get("startDate").asText());
        assertEquals(END_TIME.toString(), node.get("endDate").asText());
        
        PagedResourceList<AccountSummary> page = resultToPage(result);
        
        assertEquals(3, page.getItems().size());
        assertEquals((Integer)30, page.getTotal());
        assertEquals(SUMMARY, page.getItems().get(0));
        
        TestUtils.assertDatesWithTimeZoneEqual(START_TIME, page.getStartTime());
        TestUtils.assertDatesWithTimeZoneEqual(END_TIME, page.getEndTime());
        assertEquals(START_TIME.toString(), page.getRequestParams().get("startTime"));
        assertEquals(END_TIME.toString(), page.getRequestParams().get("endTime"));
        
        //verify paging/filtering
        assertEquals((Integer)10, page.getRequestParams().get("offsetBy"));
        assertEquals(20, page.getRequestParams().get("pageSize"));
        assertEquals("foo", page.getRequestParams().get("emailFilter"));
    }
    
    @Test
    public void searchForAccountSummaries() throws Exception {
        AccountSummarySearch payload = setAccountSummarySearch();
        
        Result result = controller.searchForAccountSummaries();
        assertEquals(200, result.status());
        
        PagedResourceList<AccountSummary> page = resultToPage(result);
        assertEquals(3, page.getItems().size());
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        
        AccountSummarySearch search = searchCaptor.getValue();
        
        assertEquals(payload, search);
    }
    
    @Test
    public void searchForAccountSummariesForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.WORKER)).build());
        
        AccountSummarySearch payload = setAccountSummarySearch();
        
        Result result = controller.searchForAccountSummariesForWorker(study.getIdentifier());
        assertEquals(200, result.status());
        
        PagedResourceList<AccountSummary> page = resultToPage(result);
        assertEquals(3, page.getItems().size());
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
        
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(payload, search);
    }
    
    private AccountSummarySearch setAccountSummarySearch() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withPageSize(100)
                .withEmailFilter("email")
                .withPhoneFilter("phone")
                .withAllOfGroups(Sets.newHashSet("group1"))
                .withNoneOfGroups(Sets.newHashSet("group2"))
                .withLanguage("en")
                .withStartTime(START_TIME)
                .withEndTime(END_TIME).build();
        TestUtils.mockPlay().withBody(search).mock();
        return search;
    }
    
    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2(String offsetKey, int pageSize) {
        List<ScheduledActivity> list = Lists.newArrayList();
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(TestUtils.getActivity1());
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        list.add(activity);
        
        return new ForwardCursorPagedResourceList<>(list, null).withRequestParam("pageSize", pageSize)
                .withRequestParam("offsetKey", offsetKey);
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return MAPPER.readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
