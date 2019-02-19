package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Result;
import play.test.Helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledActivityControllerTest {
    
    private static final String ACTIVITY_GUID = "activityGuid";

    private static final DateTime ENDS_ON = DateTime.now();
    
    private static final DateTime STARTS_ON = ENDS_ON.minusWeeks(1);
    
    private static final String OFFSET_BY = "2000";
    
    private static final String PAGE_SIZE = "77";

    private static final String HEALTH_CODE = "BBB";

    private static final DateTime ACCOUNT_CREATED_ON = DateTime.now();
    
    private static final String ID = "id";
    
    private static final String USER_AGENT = "App Name/4 SDK/2";
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(USER_AGENT);
    
    private static final Study STUDY = Study.create();  
    
    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF =
            new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };
    
    private ScheduledActivityController controller;

    @Mock
    ScheduledActivityService scheduledActivityService;
    
    @Mock
    StudyService studyService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Mock
    AccountDao accountDao;
    
    @Mock
    Study study;
    
    @Mock
    Account account;
    
    @Mock
    BridgeConfig bridgeConfig;
    
    @Captor
    ArgumentCaptor<ScheduleContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> startsOnCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> endsOnCaptor;
    
    @Captor
    private ArgumentCaptor<DateTimeZone> timeZoneCaptor;
    
    @Captor
    private ArgumentCaptor<List<ScheduledActivity>> activitiesCaptor;
    
    private SessionUpdateService sessionUpdateService;
    
    UserSession session;
    
    @Before
    public void before() throws Exception {
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setLocalScheduledOn(LocalDateTime.now().minusDays(1));
        schActivity.setActivity(TestUtils.getActivity3());
        schActivity.setReferentGuid("referentGuid");
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        TestUtils.mockPlay().withBody(list).withHeader("User-Agent", USER_AGENT).mock();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withDataGroups(USER_DATA_GROUPS)
                .withSubstudyIds(USER_SUBSTUDY_IDS)
                .withLanguages(LANGUAGES)
                .withCreatedOn(ACCOUNT_CREATED_ON)
                .withId(ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        
        when(scheduledActivityService.getScheduledActivities(eq(STUDY), any(ScheduleContext.class))).thenReturn(list);

        controller = spy(new ScheduledActivityController());
        controller.setScheduledActivityService(scheduledActivityService);
        controller.setStudyService(studyService);
        controller.setCacheProvider(cacheProvider);
        controller.setAccountDao(accountDao);
        controller.setBridgeConfig(bridgeConfig);
        
        STUDY.setIdentifier("api");
        when(studyService.getStudy(new StudyIdentifierImpl("api"))).thenReturn(STUDY);
        
        when(bridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        
        sessionUpdateService = spy(new SessionUpdateService());
        sessionUpdateService.setCacheProvider(cacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(CLIENT_INFO).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void timeZoneCapturedFirstTime() throws Exception {
        TestUtils.mockEditAccount(accountDao, account);
        
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(account).setTimeZone(MSK);
        assertEquals(MSK, session.getParticipant().getTimeZone());
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(MSK, context.getInitialTimeZone());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testZoneUsedFromPersistenceWhenAvailable() throws Exception {
        DateTimeZone UNK = DateTimeZone.forOffsetHours(4);
        StudyParticipant updatedParticipant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withTimeZone(UNK).build();
        session.setParticipant(updatedParticipant);
        
        controller.getScheduledActivities(null, "-07:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(UNK, context.getInitialTimeZone());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void utcTimeZoneParsedCorrectly() throws Exception {
        controller.getScheduledActivities(null, "+0:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals("+00:00", DateUtils.timeZoneToOffsetString(context.getInitialTimeZone()));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivtiesAssemblesCorrectContext() throws Exception {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        List<ScheduledActivity> list = Lists.newArrayList();
        scheduledActivityService = mock(ScheduledActivityService.class);
        when(scheduledActivityService.getScheduledActivities(eq(STUDY), any(ScheduleContext.class))).thenReturn(list);
        controller.setScheduledActivityService(scheduledActivityService);
        
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(MSK, context.getInitialTimeZone());
        assertEquals(USER_DATA_GROUPS, context.getCriteriaContext().getUserDataGroups());
        assertEquals(5, context.getMinimumPerSchedule());
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(HEALTH_CODE, critContext.getHealthCode());
        assertEquals(LANGUAGES, critContext.getLanguages());
        assertEquals(USER_SUBSTUDY_IDS, critContext.getUserSubstudyIds());
        assertEquals(TEST_STUDY_IDENTIFIER, critContext.getStudyIdentifier().getIdentifier());
        assertEquals(CLIENT_INFO, critContext.getClientInfo());
        
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("id", requestInfo.getUserId());
        assertEquals(LANGUAGES, requestInfo.getLanguages());
        assertEquals(USER_DATA_GROUPS, requestInfo.getUserDataGroups());
        assertEquals(USER_SUBSTUDY_IDS, requestInfo.getUserSubstudyIds());
        assertNotNull(requestInfo.getActivitiesAccessedOn());
        assertEquals(MSK, requestInfo.getActivitiesAccessedOn().getZone());
        assertEquals(MSK, requestInfo.getTimeZone());
        assertEquals(TEST_STUDY, requestInfo.getStudyIdentifier());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesAsScheduledActivitiesReturnsCorrectType() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getScheduledActivities(now.toString(), null, null, null);
        TestUtils.assertResult(result, 200);
        String output = Helpers.contentAsString(result);

        JsonNode results = BridgeObjectMapper.get().readTree(output);
        ArrayNode items = (ArrayNode)results.get("items");
        for (int i=0; i < items.size(); i++) {
            assertEquals("ScheduledActivity", items.get(i).get("type").asText());
        }
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitesAsTasks() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getTasks(now.toString(), null, null);
        TestUtils.assertResult(result, 200);
        String output = Helpers.contentAsString(result);
        
        // Verify that even without the writer, we are not leaking these values
        // through the API, and they are typed as "Task"s.
        JsonNode items = BridgeObjectMapper.get().readTree(output).get("items");
        assertTrue(items.size() > 0);
        for (int i=0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            assertNotNull(item.get("guid"));
            assertNull(item.get("healthCode"));
            assertNull(item.get("schedulePlanGuid"));
            assertEquals("Task", item.get("type").asText());
        }
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesWithUntil() throws Exception {
        // Until value is simply passed along as is to the scheduler.
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getScheduledActivities(now.toString(), null, null, null);
        TestUtils.assertResult(result, 200);
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(now, contextCaptor.getValue().getEndsOn());
        assertEquals(now.getZone(), contextCaptor.getValue().getInitialTimeZone());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesWithDaysAheadTimeZoneAndMinimum() throws Exception {
        // We expect the endsOn value to be three days from now at the end of the day 
        // (set millis to 0 so the values match at the end of the test).
        DateTime expectedEndsOn = DateTime.now()
            .withZone(DateTimeZone.forOffsetHours(3)).plusDays(3)
            .withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(0);
        
        Result result = controller.getScheduledActivities(null, "+03:00", "3", null);
        TestUtils.assertResult(result, 200);
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(expectedEndsOn, contextCaptor.getValue().getEndsOn().withMillisOfSecond(0));
        assertEquals(expectedEndsOn.getZone(), contextCaptor.getValue().getInitialTimeZone());
        assertEquals(0, contextCaptor.getValue().getMinimumPerSchedule());
        assertEquals(CLIENT_INFO, contextCaptor.getValue().getCriteriaContext().getClientInfo());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        Result result = controller.updateScheduledActivities();
        TestUtils.assertResult(result, 200);

        verify(scheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(scheduledActivityService);
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new ScheduledActivityController();
        controller.setBridgeConfig(bridgeConfig);
        controller.getScheduledActivities(DateTime.now().toString(), null, null, null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void fullyInitializedSessionProvidesAccountCreatedOnInScheduleContext() throws Exception {
        Result result = controller.getScheduledActivities(null, "-07:00", "3", null);
        TestUtils.assertResult(result, 200);
        
        verify(scheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(ACCOUNT_CREATED_ON.withZone(DateTimeZone.UTC), context.getAccountCreatedOn());
    }
    
    @Test
    public void activityHistoryWithDefaults() throws Exception {
        doReturn(createActivityResultsV2(77, null)).when(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), eq(null), eq(null), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        
        Result result = controller.getActivityHistory(ACTIVITY_GUID, null, null, null, null, null);
        TestUtils.assertResult(result, 200);

        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID), eq(null),
                eq(null), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ScheduledActivity> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>(){});
        assertNull(list.getItems().get(0).getHealthCode());
    }
    
    @Test
    public void getActivityHistoryV3() throws Exception {
        doReturn(createActivityResultsV2(20, "offsetKey")).when(scheduledActivityService).getActivityHistory(
                eq(HEALTH_CODE), eq(ActivityType.TASK), eq("referentGuid"), any(), any(), eq("offsetKey"), eq(20));
        
        Result result = controller.getActivityHistoryV3("tasks", "referentGuid", STARTS_ON.toString(),
                ENDS_ON.toString(), "offsetKey", "20");
        TestUtils.assertResult(result, 200);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        assertEquals(20, (int)page.getRequestParams().get("pageSize"));
        assertEquals("offsetKey", (String)page.getRequestParams().get("offsetKey"));
        
        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ActivityType.TASK), eq("referentGuid"),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("offsetKey"), eq(20));
        assertEquals(STARTS_ON.toString(), startsOnCaptor.getValue().toString());
        assertEquals(ENDS_ON.toString(), endsOnCaptor.getValue().toString());
    }
    
    @Test
    public void getActivityHistoryV3SetsNullDefaults() throws Exception {
        Result result = controller.getActivityHistoryV3("wrongtypes", null, null, null, null, null);
        TestUtils.assertResult(result, 200);

        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(null), eq(null),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        assertNull(startsOnCaptor.getValue());
        assertNull(endsOnCaptor.getValue());
    }
    
    @Test
    public void activityHistoryWithAllValues() throws Exception {
        doReturn(createActivityResultsV2(77, "2000")).when(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(), any(), eq("2000"), eq(77));
        
        Result result = controller.getActivityHistory(ACTIVITY_GUID, STARTS_ON.toString(),
                ENDS_ON.toString(), OFFSET_BY, null, PAGE_SIZE);
        TestUtils.assertResult(result, 200);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(OFFSET_BY, node.get("offsetBy").asText());
        
        assertEquals(1, page.getItems().size());
        assertEquals("777", page.getNextPageOffsetKey());
        assertEquals(77, page.getRequestParams().get("pageSize"));
        assertEquals(OFFSET_BY, page.getRequestParams().get("offsetKey"));

        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID), startsOnCaptor.capture(),
                endsOnCaptor.capture(), eq("2000"), eq(77));
        assertTrue(STARTS_ON.isEqual(startsOnCaptor.getValue()));
        assertTrue(ENDS_ON.isEqual(endsOnCaptor.getValue()));
    }
    
    @Test
    public void activityHistoryWithOffsetKey() throws Exception {
        doReturn(createActivityResultsV2(77, "2000")).when(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(), any(), eq("2000"), eq(77));

        Result result = controller.getActivityHistory(ACTIVITY_GUID, STARTS_ON.toString(),
                ENDS_ON.toString(), null, OFFSET_BY, PAGE_SIZE);
        TestUtils.assertResult(result, 200);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        assertEquals(1, page.getItems().size());
        assertEquals("777", page.getNextPageOffsetKey());
        assertEquals(77, page.getRequestParams().get("pageSize"));
        assertEquals(OFFSET_BY, page.getRequestParams().get("offsetKey"));

        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID), startsOnCaptor.capture(),
                endsOnCaptor.capture(), eq("2000"), eq(77));
        assertTrue(STARTS_ON.isEqual(startsOnCaptor.getValue()));
        assertTrue(ENDS_ON.isEqual(endsOnCaptor.getValue()));
    }
    
    @Test
    public void updateScheduledActivitiesWithClientData() throws Exception {
        JsonNode clientData = TestUtils.getClientData();
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setLocalScheduledOn(LocalDateTime.now().minusDays(1));
        schActivity.setActivity(TestUtils.getActivity3());
        schActivity.setClientData(clientData);
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        TestUtils.mockPlay().withBody(list).mock();
        
        Result result = controller.updateScheduledActivities();
        TestUtils.assertResult(result, 200);
        
        verify(scheduledActivityService).updateScheduledActivities(eq(HEALTH_CODE), activitiesCaptor.capture());
        
        List<ScheduledActivity> capturedActivities = activitiesCaptor.getValue();
        assertEquals(clientData, capturedActivities.get(0).getClientData());
    }
    
    @Test
    public void getScheduledActivitiesV4() throws Exception {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        DateTime startsOn = DateTime.now(zone).minusMinutes(1);
        DateTime endsOn = DateTime.now(zone).plusDays(7);
        
        Result result = controller.getScheduledActivitiesByDateRange(startsOn.toString(), endsOn.toString());
        TestUtils.assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(startsOn.toString(), node.get("startTime").asText());
        assertEquals(endsOn.toString(), node.get("endTime").asText());
        
        verify(sessionUpdateService).updateTimeZone(any(UserSession.class), timeZoneCaptor.capture());
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        verify(scheduledActivityService).getScheduledActivitiesV4(eq(STUDY), contextCaptor.capture());
        
        assertEquals(startsOn.getZone(), timeZoneCaptor.getValue());
        
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("id", requestInfo.getUserId());
        assertEquals(CLIENT_INFO, requestInfo.getClientInfo());
        assertEquals(USER_AGENT, requestInfo.getUserAgent());
        assertEquals(LANGUAGES, requestInfo.getLanguages());
        assertEquals(USER_DATA_GROUPS, requestInfo.getUserDataGroups());
        assertEquals(USER_SUBSTUDY_IDS, requestInfo.getUserSubstudyIds());
        assertTrue(requestInfo.getActivitiesAccessedOn().isAfter(startsOn));
        assertNull(requestInfo.getSignedInOn());
        assertEquals(zone, requestInfo.getTimeZone());
        assertEquals(TEST_STUDY, requestInfo.getStudyIdentifier());
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(startsOn.getZone(), context.getInitialTimeZone());
        // To make the range inclusive, we need to adjust timestamp to right before the start instant
        // This value is not mirrored back in the response (see test above of the response).
        assertEquals(startsOn.minusMillis(1), context.getStartsOn());
        assertEquals(endsOn, context.getEndsOn());
        assertEquals(0, context.getMinimumPerSchedule());
        assertEquals(ACCOUNT_CREATED_ON.withZone(DateTimeZone.UTC), context.getAccountCreatedOn());
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(TEST_STUDY, critContext.getStudyIdentifier());
        assertEquals(HEALTH_CODE, critContext.getHealthCode());
        assertEquals(ID, critContext.getUserId());
        assertEquals(CLIENT_INFO, critContext.getClientInfo());
        assertEquals(USER_DATA_GROUPS, critContext.getUserDataGroups());
        assertEquals(USER_SUBSTUDY_IDS, critContext.getUserSubstudyIds());
        assertEquals(LANGUAGES, critContext.getLanguages());
    }
    
    @Test(expected = BadRequestException.class)
    public void getScheduledActivitiesMissingStartsOn() throws Exception {
        DateTime endsOn = DateTime.now().plusDays(7);
        controller.getScheduledActivitiesByDateRange(null, endsOn.toString());
    }
    
    @Test(expected = BadRequestException.class)
    public void getScheduledActivitiesMissingEndsOn() throws Exception {
        DateTime startsOn = DateTime.now().plusDays(7);
        controller.getScheduledActivitiesByDateRange(startsOn.toString(), null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getScheduledActivitiesMalformattedDateTimeStampOn() throws Exception {
        controller.getScheduledActivitiesByDateRange("2010-01-01", null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getScheduledActivitiesMismatchedTimeZone() throws Exception {
        DateTime startsOn = DateTime.now(DateTimeZone.forOffsetHours(4));
        DateTime endsOn = DateTime.now(DateTimeZone.forOffsetHours(-7)).plusDays(7);
        controller.getScheduledActivitiesByDateRange(startsOn.toString(), endsOn.toString());
    }
    
    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2(int pageSize, String offsetKey) {
        List<ScheduledActivity> list = Lists.newArrayList();
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(TestUtils.getActivity1());
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        list.add(activity);
        
        return new ForwardCursorPagedResourceList<ScheduledActivity>(list, "777")
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }
}
