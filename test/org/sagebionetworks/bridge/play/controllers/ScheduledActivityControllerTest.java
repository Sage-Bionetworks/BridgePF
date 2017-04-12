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
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
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
import com.google.common.collect.Sets;

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
    
    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };
    
    private ScheduledActivityController controller;
    
    private ClientInfo clientInfo;

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
    private ArgumentCaptor<List<ScheduledActivity>> activitiesCaptor;
    
    UserSession session;
    
    @Before
    public void before() throws Exception {
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setLocalScheduledOn(LocalDateTime.now().minusDays(1));
        schActivity.setActivity(TestUtils.getActivity3());
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        TestUtils.mockPlayContextWithJson(json);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withDataGroups(Sets.newHashSet("group1"))
                .withLanguages(TestUtils.newLinkedHashSet("en","fr"))
                .withCreatedOn(ACCOUNT_CREATED_ON)
                .withId(ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        when(scheduledActivityService.getScheduledActivities(any(ScheduleContext.class))).thenReturn(list);

        doReturn(ACCOUNT_CREATED_ON).when(account).getCreatedOn();
        doReturn(study).when(studyService).getStudy(TestConstants.TEST_STUDY_IDENTIFIER);

        controller = spy(new ScheduledActivityController());
        controller.setScheduledActivityService(scheduledActivityService);
        controller.setStudyService(studyService);
        controller.setCacheProvider(cacheProvider);
        controller.setParticipantOptionsService(optionsService);
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(cacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        clientInfo = ClientInfo.fromUserAgentCache("App Name/4 SDK/2");
        doReturn(clientInfo).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @Test
    public void timeZoneCapturedFirstTime() throws Exception {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(optionsService).setDateTimeZone(TestConstants.TEST_STUDY, session.getHealthCode(),
                ParticipantOption.TIME_ZONE, MSK);
        assertEquals(MSK, session.getParticipant().getTimeZone());
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(MSK, context.getInitialTimeZone());
    }
    
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
    
    @Test
    public void utcTimeZoneParsedCorrectly() throws Exception {
        controller.getScheduledActivities(null, "+0:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals("+00:00", DateUtils.timeZoneToOffsetString(context.getInitialTimeZone()));
        
    }
    
    @Test
    public void getScheduledActivtiesAssemblesCorrectContext() throws Exception {
        List<ScheduledActivity> list = Lists.newArrayList();
        scheduledActivityService = mock(ScheduledActivityService.class);
        when(scheduledActivityService.getScheduledActivities(any(ScheduleContext.class))).thenReturn(list);
        controller.setScheduledActivityService(scheduledActivityService);
        
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(scheduledActivityService).getScheduledActivities(contextCaptor.capture());
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(DateTimeZone.forOffsetHours(3), context.getInitialTimeZone());
        assertEquals(Sets.newHashSet("group1"), context.getCriteriaContext().getUserDataGroups());
        assertEquals(5, context.getMinimumPerSchedule());
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(HEALTH_CODE, critContext.getHealthCode());
        assertEquals(TestUtils.newLinkedHashSet("en","fr"), critContext.getLanguages());
        assertEquals("api", critContext.getStudyIdentifier().getIdentifier());
        assertEquals(clientInfo, critContext.getClientInfo());
        
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("id", requestInfo.getUserId());
        assertEquals(TestUtils.newLinkedHashSet("en","fr"), requestInfo.getLanguages());
        assertEquals(Sets.newHashSet("group1"), requestInfo.getUserDataGroups());
        assertNotNull(requestInfo.getActivitiesAccessedOn());
        assertEquals(DateTimeZone.forOffsetHours(3), requestInfo.getTimeZone());
        assertEquals(TestConstants.TEST_STUDY, requestInfo.getStudyIdentifier());
    }
    
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
        assertEquals(clientInfo, contextCaptor.getValue().getCriteriaContext().getClientInfo());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        controller.updateScheduledActivities();
        verify(scheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(scheduledActivityService);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new ScheduledActivityController();
        controller.getScheduledActivities(DateTime.now().toString(), null, null, null);
    }
    
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
        assertEquals("777", page.getOffsetBy());
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
