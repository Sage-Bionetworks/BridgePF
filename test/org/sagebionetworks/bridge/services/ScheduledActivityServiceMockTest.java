package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ScheduledActivityServiceMockTest {
    
    private static final HashSet<Object> EMPTY_SET = Sets.newHashSet();

    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final String HEALTH_CODE = "BBB";
    
    private ScheduledActivityService service;
    
    private SchedulePlanService schedulePlanService;
    
    private User user;
    
    private ScheduledActivityDao activityDao;
    
    private ActivityEventService activityEventService;
    
    private DateTime endsOn;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        user = new User();
        user.setStudyKey(TEST_STUDY.getIdentifier());
        user.setHealthCode(HEALTH_CODE);
        user.setAccountCreatedOn(ENROLLMENT.minusHours(3));
        
        endsOn = DateTime.now().plusDays(2);
        
        service = new ScheduledActivityService();
        
        schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY)).thenReturn(TestUtils.getSchedulePlans(TEST_STUDY));

        Map<String,DateTime> map = ImmutableMap.of();
        activityEventService = mock(ActivityEventService.class);
        when(activityEventService.getActivityEventMap(anyString())).thenReturn(map);
        
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        
        activityDao = mock(DynamoScheduledActivityDao.class);
        when(activityDao.getActivity(any(), anyString(), anyString())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
            schActivity.setHealthCode((String)args[1]);
            schActivity.setGuid((String)args[2]);
            return schActivity;
        });
        when(activityDao.getActivities(context.getZone(), scheduledActivities)).thenReturn(scheduledActivities);
        
        service.setSchedulePlanService(schedulePlanService);
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getScheduledActivities(user, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withTimeZone(DateTimeZone.UTC).withEndsOn(DateTime.now().minusSeconds(1)).build());
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getScheduledActivities(user, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(DateTime.now().plusDays(ScheduleContextValidator.MAX_EXPIRES_ON_DAYS).plusSeconds(1)).build());
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfActivitiesWithNullElement() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        scheduledActivities.set(0, (DynamoScheduledActivity)null);
        
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
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withTimeZone(DateTimeZone.UTC)
        .withAccountCreatedOn(ENROLLMENT.minusHours(2)).withEndsOn(endsOn).withHealthCode(HEALTH_CODE)
        .build();        
        
        List<ScheduledActivity> activities = service.getScheduledActivities(user, context);
        assertEquals(3, activities.size());
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void updateActivitiesWorks() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        
        // 4-5 activities (depending on time of day), finish two, these should publish events, 
        // the third with a startedOn timestamp will be saved, so 3 activities sent to the DAO
        assertTrue(scheduledActivities.size() >= 3);
        scheduledActivities.get(0).setStartedOn(DateTime.now().getMillis());
        scheduledActivities.get(1).setFinishedOn(DateTime.now().getMillis());
        scheduledActivities.get(2).setFinishedOn(DateTime.now().getMillis());
        
        ArgumentCaptor<List> updateCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ScheduledActivity> publishCapture = ArgumentCaptor.forClass(ScheduledActivity.class);
        
        service.updateScheduledActivities("BBB", scheduledActivities);
        
        verify(activityDao).updateActivities(anyString(), updateCapture.capture());
        // Three activities have timestamp updates and need to be persisted
        verify(activityDao, times(3)).getActivity(eq(null), anyString(), anyString());
        // Two activities have been finished and generate activity finished events
        verify(activityEventService, times(2)).publishActivityFinishedEvent(publishCapture.capture());
        
        List<DynamoScheduledActivity> dbActivities = (List<DynamoScheduledActivity>)updateCapture.getValue();
        assertEquals(3, dbActivities.size());
        // Correct saved activities
        assertEquals(scheduledActivities.get(0).getGuid(), dbActivities.get(0).getGuid());
        assertEquals(scheduledActivities.get(1).getGuid(), dbActivities.get(1).getGuid());
        assertEquals(scheduledActivities.get(2).getGuid(), dbActivities.get(2).getGuid());
        
        // Correct published activities
        ScheduledActivity publishedActivity1 = publishCapture.getAllValues().get(0);
        assertEquals(scheduledActivities.get(1).getGuid(), publishedActivity1.getGuid());
        ScheduledActivity publishedActivity2 = publishCapture.getAllValues().get(1);
        assertEquals(scheduledActivities.get(2).getGuid(), publishedActivity2.getGuid());
        
    }
    
    @Test(expected = BridgeServiceException.class)
    public void activityListWithNullsRejected() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.set(0, null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expected = BridgeServiceException.class)
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
        List<ScheduledActivity> scheduled = createActivities("AAA", "BBB");
        List<ScheduledActivity> db = createActivities("BBB");
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("AAA","BBB"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("AAA"), toGuids(saves));
    }
    
    @Test
    public void persistedAndScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("CCC");
        List<ScheduledActivity> db = createActivities("CCC");
        db.get(0).setStartedOn(DateTime.now().getMillis());
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        // Verifying that it exists in scheduled and was replaced with persisted version
        assertNotNull(scheduled.get(0).getStartedOn());
        assertEquals(EMPTY_SET, toGuids(saves));
    }
    
    @Test
    public void startedNotScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "CCC");
        List<ScheduledActivity> db = createActivities("CCC");
        db.get(0).setStartedOn(new Long(1234L)); // started, not scheduled
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("AAA","CCC"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("AAA"), toGuids(saves));
    }
    
    @Test
    public void expiredTasksExcludedFromCalculations() {
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
        
        // create activities in the past that are now expired.
        List<ScheduledActivity> scheduled = createOldActivities(PST, "AAA","BBB");
        List<ScheduledActivity> db = createOldActivities(PST, "AAA","CCC");
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertTrue(scheduled.isEmpty());
        assertEquals(EMPTY_SET, toGuids(saves));
    }
    
    @Test
    public void finishedTasksExcludedFromResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "BBB", "CCC");
        List<ScheduledActivity> db = createActivities("AAA", "BBB");
        db.get(0).setFinishedOn(DateTime.now().getMillis()); // AAA will not be in results
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("BBB","CCC"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("CCC"), toGuids(saves));
    }
    
    @Test
    public void newAndExistingActivitiesAreMerged() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "BBB", "CCC");
        List<ScheduledActivity> db = createActivities("AAA","CCC");
        ScheduledActivity activity = db.stream()
                .filter(act -> act.getGuid().equals("AAA")).findFirst().get();
        activity.setStartedOn(DateTime.now().getMillis());
        
        List<ScheduledActivity> saves = service.updateActivitiesAndCollectSaves(scheduled, db);
        scheduled = service.orderActivities(scheduled);
        
        assertEquals(Sets.newHashSet("AAA","BBB","CCC"), toGuids(scheduled));
        assertEquals(Sets.newHashSet("BBB"), toGuids(saves));
        
        activity = scheduled.stream()
                .filter(act -> act.getGuid().equals("AAA")).findFirst().get();
        assertTrue(activity.getStartedOn() > 0L);
        
    }
    
    @Test
    public void orderActivitieFiltersAndSorts() {
        DateTime time1 = DateTime.parse("2014-10-01T00:00:00.000Z");
        DateTime time2 = DateTime.parse("2014-10-02T00:00:00.000Z");
        DateTime time3 = DateTime.parse("2014-10-03T00:00:00.000Z");
        DateTime time4 = DateTime.parse("2014-10-04T00:00:00.000Z");
        
        List<ScheduledActivity> list = createActivities("AAA", "BBB", "CCC", "DDD");
        list.get(0).setScheduledOn(time2);
        list.get(1).setScheduledOn(time1);
        list.get(2).setScheduledOn(time4);
        list.get(3).setScheduledOn(time3);
        list.get(3).setExpiresOn(DateTime.now().minusDays(1));
        
        List<ScheduledActivity> result = service.orderActivities(list);
        assertEquals(3, result.size());
        assertEquals(time1, result.get(0).getScheduledOn());
        assertEquals(time2, result.get(1).getScheduledOn());
        assertEquals(time4, result.get(2).getScheduledOn());
        
    }
    
    @Test
    public void complexCriteriaBasedScheduleWorksThroughService() throws Exception {
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
        events.put("enrollment", DateTime.now().minusDays(3));
        when(activityEventService.getActivityEventMap("AAA")).thenReturn(events);
        
        ClientInfo info = ClientInfo.fromUserAgentCache("Parkinson-QA/36 (iPhone 5S; iPhone OS/9.2.1) BridgeSDK/7");
        
        SchedulePlan voiceActivityPlan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        List<SchedulePlan> schedulePlans = Lists.newArrayList(voiceActivityPlan);
        when(schedulePlanService.getSchedulePlans(info, new StudyIdentifierImpl("test-study"))).thenReturn(schedulePlans);
        
        User user = new User();
        user.setDataGroups(Sets.newHashSet("parkinson","test_user"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withClientInfo(info)
            .withStudyIdentifier("test-study")
            .withUserDataGroups(user.getDataGroups())
            .withEndsOn(DateTime.now().plusDays(1).withTimeAtStartOfDay())
            .withTimeZone(DateTimeZone.UTC)
            .withHealthCode("AAA")
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .build();
        
        // Is a parkinson patient, gets 3 tasks
        List<ScheduledActivity> schActivities = service.getScheduledActivities(user, context);
        assertEquals(3, schActivities.size());
        
        // Not a parkinson patient, get 1 task
        context = new ScheduleContext.Builder()
                .withContext(context)
                .withUserDataGroups(Sets.newHashSet("test_user")).build();
        schActivities = service.getScheduledActivities(user, context);
        assertEquals(1, schActivities.size());
    }
    
    private List<ScheduledActivity> createActivities(String... guids) {
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setGuid(guid);
            activity.setTimeZone(DateTimeZone.UTC);
            activity.setScheduledOn(DateTime.now());
            list.add(activity);
        }
        return list;
    }
    
    private List<ScheduledActivity> createOldActivities(DateTimeZone timeZone, String... guids) {
        DateTime startedOn = DateTime.now().minusMonths(6);
        DateTime expiresOn = DateTime.now().minusMonths(5);
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setTimeZone(timeZone);
            activity.setGuid(guid);
            activity.setScheduledOn(startedOn);
            activity.setExpiresOn(expiresOn);
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
        
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withTimeZone(DateTimeZone.UTC)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2)).withEndsOn(endsOn).withHealthCode(HEALTH_CODE)
                .withEvents(events).build();
    }
    
}
