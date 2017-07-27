package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Result;
import play.test.Helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
    ParticipantOptionsService optionsService;
    
    @Mock
    Study study;
    
    @Mock
    Account account;
    
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
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        Map<String,String[]> headers = Maps.newHashMap();
        headers.put("User-Agent", new String[]{USER_AGENT});
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        TestUtils.mockPlayContextWithJson(json, headers);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withDataGroups(USER_DATA_GROUPS)
                .withLanguages(LANGUAGES)
                .withCreatedOn(ACCOUNT_CREATED_ON)
                .withId(ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        
        when(scheduledActivityService.getScheduledActivities(any(ScheduleContext.class))).thenReturn(list);

        doReturn(ACCOUNT_CREATED_ON).when(account).getCreatedOn();
        doReturn(study).when(studyService).getStudy(TEST_STUDY_IDENTIFIER);

        controller = spy(new ScheduledActivityController());
        controller.setScheduledActivityService(scheduledActivityService);
        controller.setStudyService(studyService);
        controller.setCacheProvider(cacheProvider);
        controller.setParticipantOptionsService(optionsService);
        
        sessionUpdateService = spy(new SessionUpdateService());
        sessionUpdateService.setCacheProvider(cacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(CLIENT_INFO).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void timeZoneCapturedFirstTime() throws Exception {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(optionsService).setDateTimeZone(TEST_STUDY, session.getHealthCode(),
                ParticipantOption.TIME_ZONE, MSK);
        assertEquals(MSK, session.getParticipant().getTimeZone());
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
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
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(UNK, context.getInitialTimeZone());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void utcTimeZoneParsedCorrectly() throws Exception {
        controller.getScheduledActivities(null, "+0:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals("+00:00", DateUtils.timeZoneToOffsetString(context.getInitialTimeZone()));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivtiesAssemblesCorrectContext() throws Exception {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        List<ScheduledActivity> list = Lists.newArrayList();
        scheduledActivityService = mock(ScheduledActivityService.class);
        when(scheduledActivityService.getScheduledActivities(any(ScheduleContext.class))).thenReturn(list);
        controller.setScheduledActivityService(scheduledActivityService);
        
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(MSK, context.getInitialTimeZone());
        assertEquals(USER_DATA_GROUPS, context.getCriteriaContext().getUserDataGroups());
        assertEquals(5, context.getMinimumPerSchedule());
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(HEALTH_CODE, critContext.getHealthCode());
        assertEquals(LANGUAGES, critContext.getLanguages());
        assertEquals(TEST_STUDY_IDENTIFIER, critContext.getStudyIdentifier().getIdentifier());
        assertEquals(CLIENT_INFO, critContext.getClientInfo());
        
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("id", requestInfo.getUserId());
        assertEquals(LANGUAGES, requestInfo.getLanguages());
        assertEquals(USER_DATA_GROUPS, requestInfo.getUserDataGroups());
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
        
        controller.getScheduledActivities(now.toString(), null, null, null);
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
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
        
        controller.getScheduledActivities(null, "+03:00", "3", null);
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(expectedEndsOn, contextCaptor.getValue().getEndsOn().withMillisOfSecond(0));
        assertEquals(expectedEndsOn.getZone(), contextCaptor.getValue().getInitialTimeZone());
        assertEquals(0, contextCaptor.getValue().getMinimumPerSchedule());
        assertEquals(CLIENT_INFO, contextCaptor.getValue().getCriteriaContext().getClientInfo());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        controller.updateScheduledActivities();
        verify(scheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(scheduledActivityService);
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new ScheduledActivityController();
        controller.getScheduledActivities(DateTime.now().toString(), null, null, null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void fullyInitializedSessionProvidesAccountCreatedOnInScheduleContext() throws Exception {
        controller.getScheduledActivities(null, "-07:00", "3", null);
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(ACCOUNT_CREATED_ON.withZone(DateTimeZone.UTC), context.getAccountCreatedOn());
    }
    
    @Test
    public void activityHistoryWithDefaults() throws Exception {
        doReturn(createActivityResultsV2(77)).when(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(null), any(null), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        
        Result result = controller.getActivityHistory(ACTIVITY_GUID, null, null, null, null);
        assertEquals(200, result.status());

        verify(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID), eq(null),
                eq(null), eq(null), eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ScheduledActivity> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>(){});
        assertNull(list.getItems().get(0).getHealthCode());
    }
    
    @Test
    public void activityHistoryWithAllValues() throws Exception {
        doReturn(createActivityResultsV2(77)).when(scheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(), any(), eq("2000"), eq(77));
        
        Result result = controller.getActivityHistory(ACTIVITY_GUID, STARTS_ON.toString(),
                ENDS_ON.toString(), OFFSET_BY, PAGE_SIZE);
        assertEquals(200, result.status());
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        assertEquals(1, page.getItems().size());
        assertEquals("777", page.getOffsetKey());
        assertEquals(77, page.getPageSize());

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
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.updateScheduledActivities();
        assertEquals(200, result.status());
        
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
        assertEquals(200, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(startsOn.toString(), node.get("startTime").asText());
        assertEquals(endsOn.toString(), node.get("endTime").asText());
        
        verify(sessionUpdateService).updateTimeZone(any(UserSession.class), timeZoneCaptor.capture());
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        verify(scheduledActivityService).getScheduledActivitiesV4(contextCaptor.capture());
        
        assertEquals(startsOn.getZone(), timeZoneCaptor.getValue());
        
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("id", requestInfo.getUserId());
        assertEquals(CLIENT_INFO, requestInfo.getClientInfo());
        assertEquals(USER_AGENT, requestInfo.getUserAgent());
        assertEquals(LANGUAGES, requestInfo.getLanguages());
        assertEquals(USER_DATA_GROUPS, requestInfo.getUserDataGroups());
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
    
    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2(int pageSize) {
        List<ScheduledActivity> list = Lists.newArrayList();
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(TestUtils.getActivity1());
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        list.add(activity);
        
        return new ForwardCursorPagedResourceList<>(list, "777", pageSize);
    }
}
