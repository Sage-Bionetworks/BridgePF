package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledActivityServiceMockTest {
    
    private static final HashSet<Object> EMPTY_SET = Sets.newHashSet();

    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final String HEALTH_CODE = "BBB";
    
    private static final String USER_ID = "CCC";
    
    private static final String SURVEY_GUID = "surveyGuid";
    
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2015-04-03T10:40:34.000-07:00");
    
    private static final DateTime NOW = DateTime.parse("2017-02-23T14:25:51.195-08:00");
    
    private static final String ACTIVITY_GUID = "activityGuid";
    
    private static final DateTime STARTS_ON = DateTime.now().minusDays(1);
    
    private static final DateTime ENDS_ON = DateTime.now();
    
    private ScheduledActivityService service;
    
    @Mock
    private SchedulePlanService schedulePlanService;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private SurveyService surveyService;
    
    @Mock
    private Survey survey;
    
    private DateTime endsOn;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        endsOn = NOW.plusDays(2);
        
        service = new ScheduledActivityService();
        
        when(schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY)).thenReturn(TestUtils.getSchedulePlans(TEST_STUDY));

        Map<String,DateTime> map = ImmutableMap.of();
        when(activityEventService.getActivityEventMap(anyString())).thenReturn(map);
        
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        
        when(activityDao.getActivity(anyString(), anyString())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
            schActivity.setHealthCode((String)args[0]);
            schActivity.setGuid((String)args[1]);
            return schActivity;
        });
        when(activityDao.getActivities(context.getInitialTimeZone(), scheduledActivities)).thenReturn(scheduledActivities);
        
        doReturn(SURVEY_GUID).when(survey).getGuid();
        doReturn(SURVEY_CREATED_ON.getMillis()).when(survey).getCreatedOn();
        doReturn("identifier").when(survey).getIdentifier();
        when(surveyService.getSurveyMostRecentlyPublishedVersion(
                eq(TEST_STUDY), any())).thenReturn(survey);
        
        service.setSchedulePlanService(schedulePlanService);
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSurveyService(surveyService);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test(expected = BadRequestException.class)
    public void activityHistoryEnforcesMinPageSize() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, ENDS_ON, null, 2);
    }
    
    @Test(expected = BadRequestException.class)
    public void activityHistoryEnforcesMaxPageSize() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, ENDS_ON, null, 200);
    }
    
    @Test(expected = BadRequestException.class)
    public void activityHistoryEnforcesFullDateRangeWhenNoStart() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, ENDS_ON, null, 40);
    }
    
    @Test(expected = BadRequestException.class)
    public void activityHistoryEnforcesFullDateRangeWhenNoEnd() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, null, null, 40);
    }
    
    @Test
    public void activityHistoryDefaultsDateRange() {
        DateTimeUtils.setCurrentMillisFixed(STARTS_ON.getMillis());
        
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, null, null, 40);
        verify(activityDao).getActivityHistoryV2(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON.minusDays(9), STARTS_ON.plusDays(5), null, 40);
        
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test(expected = BadRequestException.class)
    public void activityHistoryEnforcesDateRangeEndAfterStart() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, ENDS_ON, STARTS_ON, null, 200);
    }

    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getScheduledActivities(new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withInitialTimeZone(DateTimeZone.UTC).withEndsOn(NOW.minusSeconds(1)).build());
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getScheduledActivities(new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(NOW.plusDays(ScheduleContextValidator.MAX_EXPIRES_ON_DAYS).plusSeconds(1)).build());
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfActivitiesWithNullElement() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        scheduledActivities.set(0, null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsListOfActivitiesWithTaskThatLacksGUID() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        scheduledActivities.get(0).setGuid(null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @Test
    public void missingEnrollmentEventIsSuppliedFromAccountCreatedOn() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(DateTimeZone.UTC)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2))
                .withEndsOn(endsOn)
                .withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID).build();        
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        assertTrue(activities.size() > 0);
    }
    
    @Test
    public void surveysAreResolved() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(DateTimeZone.UTC)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2))
                .withEndsOn(endsOn)
                .withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID).build();        
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        //noinspection Convert2streamapi
        for (ScheduledActivity activity : activities) {
            if (activity.getActivity().getActivityType() == ActivityType.SURVEY) {
                assertEquals(SURVEY_CREATED_ON.getMillis(),
                        activity.getActivity().getSurvey().getCreatedOn().getMillis());
            }
        }
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void updateActivitiesWorks() throws Exception {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        
        int count = scheduledActivities.size();
        scheduledActivities.get(0).setStartedOn(NOW.getMillis());
        scheduledActivities.get(1).setFinishedOn(NOW.getMillis());
        scheduledActivities.get(2).setFinishedOn(NOW.getMillis());
        scheduledActivities.get(3).setClientData(TestUtils.getClientData());
        
        ArgumentCaptor<List> updateCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ScheduledActivity> publishCapture = ArgumentCaptor.forClass(ScheduledActivity.class);
        
        service.updateScheduledActivities("BBB", scheduledActivities);
        
        verify(activityDao).updateActivities(anyString(), updateCapture.capture());
        // Three activities have timestamp updates and need to be persisted
        verify(activityDao, times(count)).getActivity(anyString(), anyString());
        // Two activities have been finished and generate activity finished events
        verify(activityEventService, times(2)).publishActivityFinishedEvent(publishCapture.capture());
        
        List<DynamoScheduledActivity> dbActivities = (List<DynamoScheduledActivity>)updateCapture.getValue();
        assertEquals(4, dbActivities.size());
        
        // Correct saved activities
        assertEquals(scheduledActivities.get(0).getGuid(), dbActivities.get(0).getGuid());
        assertEquals(scheduledActivities.get(1).getGuid(), dbActivities.get(1).getGuid());
        assertEquals(scheduledActivities.get(2).getGuid(), dbActivities.get(2).getGuid());
        assertEquals(scheduledActivities.get(3).getClientData(), dbActivities.get(3).getClientData());
        
        // Correct published activities
        ScheduledActivity publishedActivity1 = publishCapture.getAllValues().get(0);
        assertEquals(scheduledActivities.get(1).getGuid(), publishedActivity1.getGuid());
        ScheduledActivity publishedActivity2 = publishCapture.getAllValues().get(1);
        assertEquals(scheduledActivities.get(2).getGuid(), publishedActivity2.getGuid());
    }
    
    @Test(expected = BadRequestException.class)
    public void activityListsWithTooLargeClientDataRejected() throws Exception {
        JsonNode node = TestUtils.getClientData();
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (int i=0; i < 35; i++) {
            array.add(node);
        }
        
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.get(0).setClientData(array);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expected = BadRequestException.class)
    public void activityListWithNullsRejected() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.set(0, null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expected = BadRequestException.class)
    public void activityListWithNullGuidRejected() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.get(1).setGuid(null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test
    public void deleteActivitiesDoesDelete() {
        service.deleteActivitiesForUser("BBB");
        
        verify(activityDao).deleteActivitiesForUser("BBB");
        verifyNoMoreInteractions(activityDao);
    }

    @Test
    public void deleteScheduledActivitiesForUser() {
        service.deleteActivitiesForUser("AAA");
        verify(activityDao).deleteActivitiesForUser("AAA");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void deleteActivitiesForUserRejectsBadValue() {
        service.deleteActivitiesForUser(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void deleteActivitiesForSchedulePlanRejectsBadValue() {
        service.deleteActivitiesForUser("  ");
    }

    @Test
    public void newActivitiesIncludedInSaveAndResults() {
        List<ScheduledActivity> scheduled = createNewActivities("AAA", "BBB");
        List<ScheduledActivity> db = createStartedActivities("BBB");
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("AAA","BBB"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("AAA"), toGuids(saves));
    }
    
    @Test
    public void persistedAndScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createNewActivities("CCC");
        List<ScheduledActivity> db = createStartedActivities("CCC");

        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        // Verifying that it exists in scheduled and was replaced with persisted version
        assertNotNull(scheduled.get(0).getStartedOn());
        assertEquals(EMPTY_SET, toGuids(saves));
    }

    @Test
    public void expiredTasksExcludedFromCalculations() {
        // create activities in the past that are now expired.
        List<ScheduledActivity> scheduled = createExpiredActivities("AAA","BBB");
        List<ScheduledActivity> db = createExpiredActivities("AAA","CCC");
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertTrue(scheduled.isEmpty());
        assertEquals(EMPTY_SET, toGuids(saves));
    }
    
    @Test
    public void finishedTasksExcludedFromResults() {
        List<ScheduledActivity> scheduled = createNewActivities("AAA", "BBB", "CCC");
        List<ScheduledActivity> db = createStartedActivities("AAA", "BBB");
        db.get(0).setFinishedOn(NOW.getMillis()); // AAA will not be in results
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("BBB","CCC"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("CCC"), toGuids(saves));
    }
    
    @Test
    public void newAndExistingActivitiesAreMerged() {
        List<ScheduledActivity> scheduled = createNewActivities("AAA", "BBB", "CCC");
        List<ScheduledActivity> db = createStartedActivities("AAA","CCC");

        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("AAA","BBB","CCC"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("BBB"), toGuids(saves));
        
        ScheduledActivity activity = scheduled.stream()
                .filter(act -> act.getGuid().equals("AAA")).findFirst().get();
        assertTrue(activity.getStartedOn() > 0L);
        
    }

    @Test
    public void taskNotStartedIsUpdated() {
        // Activity in DDB has survey reference pointing to createdOn 1234. Newly created activity has survey reference
        // pointing to createdOn 5678. Activity in DDB hasn't been started. We should update the activity in DDB.

        // Create task references and activities.
        Activity oldActivity = new Activity.Builder().withSurvey("my-survey", "my-survey-guid", new DateTime(1234))
                .build();
        Activity newActivity = new Activity.Builder().withSurvey("my-survey", "my-survey-guid", new DateTime(5678))
                .build();

        // Create scheduled activities.
        List<ScheduledActivity> db = createNewActivities("CCC");
        db.get(0).setActivity(oldActivity);
        List<ScheduledActivity> scheduled = createNewActivities("CCC");
        scheduled.get(0).setActivity(newActivity);

        // execute and validate
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);

        // We save the task, because we want to update it.
        assertEquals(ImmutableSet.of("CCC"), toGuids(saves));

        // Verify that the schedule contains the new surveyCreatedOn
        assertEquals(1, scheduled.size());
        assertEquals(5678, scheduled.get(0).getActivity().getSurvey().getCreatedOn().getMillis());
    }

    @Test
    public void orderActivitieFiltersAndSorts() {
        DateTime time1 = DateTime.parse("2014-10-01T00:00:00.000Z");
        DateTime time2 = DateTime.parse("2014-10-02T00:00:00.000Z");
        DateTime time3 = DateTime.parse("2014-10-03T00:00:00.000Z");
        DateTime time4 = DateTime.parse("2014-10-04T00:00:00.000Z");
        
        List<ScheduledActivity> list = createNewActivities("AAA", "BBB", "CCC", "DDD");
        list.get(0).setLocalScheduledOn(time2.toLocalDateTime());
        list.get(1).setLocalScheduledOn(time1.toLocalDateTime());
        list.get(2).setLocalScheduledOn(time4.toLocalDateTime());
        list.get(3).setLocalScheduledOn(time3.toLocalDateTime());
        list.get(3).setLocalExpiresOn(NOW.toLocalDateTime().minusDays(1));
        
        List<ScheduledActivity> result = service.orderActivities(list);
        assertEquals(3, result.size());
        assertEquals(time1, result.get(0).getScheduledOn());
        assertEquals(time2, result.get(1).getScheduledOn());
        assertEquals(time4, result.get(2).getScheduledOn());
        
    }
    
    @Test
    public void complexCriteriaBasedScheduleWorksThroughService() throws Exception {
        // As long as time zone is consistent, the right number of tasks will be generated on 
        // the day of the request, regardless of the hour of the day.
        executeComplexTestInTimeZone(3, DateTimeZone.forOffsetHours(-7));
        executeComplexTestInTimeZone(23, DateTimeZone.forOffsetHours(-7));
        executeComplexTestInTimeZone(3, DateTimeZone.forOffsetHours(8));
        executeComplexTestInTimeZone(23, DateTimeZone.forOffsetHours(8));
    }
    
    private void executeComplexTestInTimeZone(int hourOfDay, DateTimeZone timeZone) throws Exception {
        DateTime now = NOW.withZone(timeZone).withHourOfDay(hourOfDay);
        String json = TestUtils.createJson("{"+  
                "'guid':'5fe9029e-beb6-4163-ac35-23d048deeefe',"+
                "'label':'Voice Activity',"+
                "'version':4,"+
                "'modifiedOn':'2016-03-04T20:21:10.487Z',"+
                "'strategy':{  "+
                    "'type':'CriteriaScheduleStrategy',"+
                    "'scheduleCriteria':[  "+
                        "{  "+
                            "'schedule':{"+  
                                "'scheduleType':'recurring',"+
                                "'eventId':'enrollment',"+
                                "'activities':[  "+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'33669208-1d07-4b89-8ec5-1eb5aad6dd75',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "},"+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'822f7666-ce7b-4854-98ec-8a6fffa708d9',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "},"+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'644dfee6-eb88-49b4-9472-a8ef79d9865f',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "}"+
                                "],"+
                                "'persistent':false,"+
                                "'interval':'P1D',"+
                                "'expires':'PT24H',"+
                                "'times':[  "+
                                    "'00:00:00.000'"+
                                "],"+
                                "'type':'Schedule'"+
                            "},"+
                            "'criteria':{  "+
                                "'allOfGroups':['parkinson'],"+
                                "'noneOfGroups':[],"+
                                "'type':'Criteria'"+
                            "},"+
                            "'type':'ScheduleCriteria'"+
                        "},"+
                        "{  "+
                            "'schedule':{"+  
                                "'scheduleType':'recurring',"+
                                "'eventId':'enrollment',"+
                                "'activities':[  "+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'7e9514ba-b32d-4124-8977-38cb227ad285',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "}"+
                                "],"+
                                "'persistent':false,"+
                                "'interval':'P1D',"+
                                "'expires':'PT24H',"+
                                "'times':[  "+
                                    "'00:00:00.000'"+
                                "],"+
                                "'type':'Schedule'"+
                            "},"+
                            "'criteria':{"+  
                                "'allOfGroups':[],"+
                                "'noneOfGroups':[],"+
                                "'type':'Criteria'"+
                            "},"+
                            "'type':'ScheduleCriteria'"+
                        "}"+
                    "]"+
                "},"+
                "'minAppVersion':36,"+
                "'type':'SchedulePlan'"+
            "}");
            
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", now.withZone(DateTimeZone.UTC).minusDays(3));
        when(activityEventService.getActivityEventMap("AAA")).thenReturn(events);
        
        ClientInfo info = ClientInfo.fromUserAgentCache("Parkinson-QA/36 (iPhone 5S; iPhone OS/9.2.1) BridgeSDK/7");
        
        SchedulePlan voiceActivityPlan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        List<SchedulePlan> schedulePlans = Lists.newArrayList(voiceActivityPlan);
        when(schedulePlanService.getSchedulePlans(info, new StudyIdentifierImpl("test-study"))).thenReturn(schedulePlans);
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withClientInfo(info)
            .withStudyIdentifier("test-study")
            .withUserDataGroups(Sets.newHashSet("parkinson","test_user"))
                .withEndsOn(now.plusDays(1).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59))
                .withInitialTimeZone(timeZone)
            .withHealthCode("AAA")
            .withUserId(USER_ID)
            .withNow(now)
            .withAccountCreatedOn(now.minusDays(4))
            .build();
        
        // Is a parkinson patient, gets 3 tasks (or 6 tasks late in the day, see BRIDGE-1603
        List<ScheduledActivity> schActivities = service.getScheduledActivities(context);

        // See BRIDGE-1603
        assertEquals(6, schActivities.size());
        
        // Not a parkinson patient, get 1 task
        context = new ScheduleContext.Builder()
                .withContext(context)
                .withUserDataGroups(Sets.newHashSet("test_user")).build();
        schActivities = service.getScheduledActivities(context);
        // See BRIDGE-1603
        assertEquals(2, schActivities.size());
    }
    
    @Test
    public void surveysAreCached() {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("surveyId");
        survey.setGuid("guid");
        doReturn(survey).when(surveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withInitialTimeZone(DateTimeZone.UTC)
                .withUserId("userId")
                .withAccountCreatedOn(NOW.minusDays(3))
                .withHealthCode("healthCode")
                .withEndsOn(NOW.plusDays(3))
                .withStudyIdentifier("studyId").build();
        
        Activity activity = new Activity.Builder().withLabel("Label").withSurvey("surveyId", "guid", null).build();
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval(Period.parse("P1D"));
        schedule.setActivities(Lists.newArrayList(activity));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan1 = new DynamoSchedulePlan();
        plan1.setStrategy(strategy);
        
        DynamoSchedulePlan plan2 = new DynamoSchedulePlan();
        plan2.setStrategy(strategy);
        
        doReturn(Lists.newArrayList(plan1,plan2)).when(schedulePlanService).getSchedulePlans(any(), any());
        
        List<ScheduledActivity> schActivities = service.getScheduledActivities(context);
        
        assertTrue(schActivities.size() > 1);
        for (ScheduledActivity act : schActivities) {
            assertEquals("guid", act.getActivity().getSurvey().getGuid());
        }
        
        verify(surveyService, times(1)).getSurveyMostRecentlyPublishedVersion(any(), any());
    }
    
    // These cases suggested by Dwayne, there all good to verify further we don't have a date change
    
    // Scheduler is interpreting the event to the correct date. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=-08:00, schedule=daily at 11pm, expected=2017-02-19T23:00-0800
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=+09:00, schedule=daily at 11pm, expected=2017-02-20T23:00+0900
    @Test
    public void schedulerInterpretsEventToCorrectDate() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.addTimes("23:00");
        
        String timestamp = firstTimeStampFor(-8, -8, schedule);
        assertEquals("2017-02-19T23:00:00.000-08:00", timestamp);
        
        timestamp = firstTimeStampFor(9, 9, schedule);
        assertEquals("2017-02-20T23:00:00.000+09:00", timestamp);
    }
    
    // Scheduler is interpreting the event to the right local time. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=-08:00, schedule=one hour after enrollment, expected=2017-02-19T18:00-0800
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=+09:00, schedule=one hour after enrollment, expected=2017-02-20T11:00+0900
    @Test
    public void schedulerInterpetsEventToRightLocalTime() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("PT1H"));
        
        String timestamp = firstTimeStampFor(-8, -8, schedule);
        assertEquals("2017-02-19T18:00:00.000-08:00", timestamp);
        
        timestamp = firstTimeStampFor(9, 9, schedule);
        assertEquals("2017-02-20T11:00:00.000+09:00", timestamp);
    }

    // Local times having timezone correctly applied. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=+09:00, schedule=one hour after enrollment, expected=2017-02-19T18:00+0900 (Should this be the 19th or the 20th?)
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=-08:00, schedule=one hour after enrollment, expected=2017-02-20T11:00-0800
    @Test
    public void localTimeZonesCorrectlyApplied() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("PT1H"));
        
        String timestamp = firstTimeStampFor(-8, +9, schedule);
        assertEquals("2017-02-19T18:00:00.000+09:00", timestamp);
        
        timestamp = firstTimeStampFor(9, -8, schedule);
        assertEquals("2017-02-20T11:00:00.000-08:00", timestamp);
    }
    
    @Test
    public void detectClientDataAdded() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setClientData(TestUtils.getClientData());
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemoved() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataChanged() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        JsonNode changedClientData = TestUtils.getClientData();
        ((ObjectNode)changedClientData).put("type", "ChangedNode");
        activity.setClientData(changedClientData);
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemainsNull() {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        
        ScheduledActivity activity = ScheduledActivity.create();
        
        assertFalse(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemainsSame() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setClientData(TestUtils.getClientData());
        
        assertFalse(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    private String firstTimeStampFor(int initialTZOffset, int requestTZOffset, Schedule schedule) {
        // Tests calling this method set up different mocked environment from other tests.
        reset(schedulePlanService);
        reset(activityEventService);
        
        DateTime enrollment = DateTime.parse("2017-02-20T01:00:00.000Z");
        DateTimeZone initialTimeZone = DateTimeZone.forOffsetHours(initialTZOffset);
        DateTimeZone requestTimeZone = DateTimeZone.forOffsetHours(requestTZOffset);
        DateTime now = DateTime.parse("2017-04-06T17:10:10.000Z").withZone(requestTimeZone);
        
        Map<String,DateTime> eventMap = Maps.newHashMap();
        eventMap.put("enrollment", enrollment);
        when(activityEventService.getActivityEventMap("healthCode")).thenReturn(eventMap);

        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        when(schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY)).thenReturn(Lists.newArrayList(plan));
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withUserId("userId")
                .withNow(now)
                .withAccountCreatedOn(enrollment)
                .withInitialTimeZone(initialTimeZone)
                .withEndsOn(now.plusDays(4).withZone(requestTimeZone))
                .withHealthCode("healthCode").build();
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        verify(activityEventService).getActivityEventMap("healthCode");
        verify(schedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        return activities.get(0).getScheduledOn().toString();
    }

    private List<ScheduledActivity> createNewActivities(String... guids) {
        return createActivities(false, false, guids);
    }

    private List<ScheduledActivity> createStartedActivities(String... guids) {
        return createActivities(true, false, guids);
    }

    private List<ScheduledActivity> createExpiredActivities(String... guids) {
        // Note, if a task is both "started" and "expired", the Activity.getStatus() logic makes the activity
        // "started".
        return createActivities(false, true, guids);
    }

    private List<ScheduledActivity> createActivities(boolean isStarted, boolean isExpired, String... guids) {
        DateTime startedOn = NOW.minusMonths(6);
        DateTime expiresOn = NOW.minusMonths(5);
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setGuid(guid);
            activity.setTimeZone(DateTimeZone.UTC);
            activity.setLocalScheduledOn(NOW.toLocalDateTime());

            if (isStarted) {
                activity.setStartedOn(startedOn.getMillis());
            }
            if (isExpired) {
                activity.setLocalExpiresOn(expiresOn.toLocalDateTime());
            }

            list.add(activity);
        }
        return list;
    }

    private Set<String> toGuids(List<ScheduledActivity> activities) {
        return activities.stream().map(ScheduledActivity::getGuid).collect(Collectors.toSet());
    }
    
    private ScheduleContext createScheduleContext(DateTime endsOn) {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withInitialTimeZone(DateTimeZone.UTC)
                .withNow(NOW)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2)).withEndsOn(endsOn).withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID).withEvents(events).build();
    }
    
}
