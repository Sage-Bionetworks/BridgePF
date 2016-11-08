package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledActivityServiceDuplicateTest {
    
    private static final String TEST_STUDY_ID = "test-study";
    private static final String HEALTH_CODE = "healthCode";
    private static final String USER_ID = "userId";
    private static final DateTime ENROLLMENT = DateTime.now();
    private static final String SCHEDULE_PLAN_GUID = "ABC";
    
    @Mock
    ScheduledActivityDao activityDao;
    
    @Mock
    ActivityEventService activityEventService;
    
    @Spy
    ScheduledActivityService service;
    
    ScheduleContext context;
    
    @Before
    public void before() {
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        
        // Mock activityEventService
        Map<String,DateTime> events = new ImmutableMap.Builder<String, DateTime>().put("enrollment",ENROLLMENT).build();
        doReturn(events).when(activityEventService).getActivityEventMap(HEALTH_CODE);
        
        context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY_ID)
                .withEndsOn(DateTime.now().plusDays(2).withTimeAtStartOfDay())
                .withTimeZone(DateTimeZone.UTC)
                .withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2))
                .build();
    }
    
    // BRIDGE-1589, using duplicates generated for Erin Mount's Lily account (user 1232)
    @Test
    public void testExistingDuplicationScenario() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Duplicated persisted activities.
        ScheduledActivity background = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T11:43:07.951"), false);
        ScheduledActivity backgroundDup = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T12:43:07.951"), false);
        ScheduledActivity train1 = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T11:43:07.951"), false);
        ScheduledActivity train1Dup = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T12:43:07.951"), false);
        ScheduledActivity train2 = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T11:43:07.951"), false);
        // this one will have been started... we should de-duplicate to include this activity
        ScheduledActivity train2Dup = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T12:43:07.951"), true);
        List<ScheduledActivity> dbActivities = Lists.newArrayList(background, backgroundDup, train1, train1Dup, train2,
                train2Dup);
        doReturn(dbActivities).when(activityDao).getActivities(any(), any());
        
        // Correctly scheduled one-time tasks coming from scheduler
        ScheduledActivity act1 = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act2 = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act3 = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T00:00:00.000"), false);
        List<ScheduledActivity> schedulerActivities = Lists.newArrayList(act1, act2, act3);
        doReturn(schedulerActivities).when(service).scheduleActivitiesForPlans(any());

        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There should only be 3 activities, and the actGuid3 activity should have selected the db version with state
        assertEquals(3, activities.size());
        assertEquals(1, filterByGuid(activities, "actGuid1").size());
        assertEquals(1, filterByGuid(activities, "actGuid2").size());
        assertEquals(1, filterByGuid(activities, "actGuid3").size());
        assertTrue(filterByGuid(activities, "actGuid3").get(0).getStartedOn() != null);
    }
    
    @Test
    public void testExistingCorrectScenario() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Duplicated persisted activities.
        ScheduledActivity background = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity train1 = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity train2 = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T00:00:00.000"), true);
        List<ScheduledActivity> dbActivities = Lists.newArrayList(background, train1, train2);
        doReturn(dbActivities).when(activityDao).getActivities(any(), any());
        
        // Correctly scheduled one-time tasks coming from scheduler
        ScheduledActivity act1 = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act2 = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act3 = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T00:00:00.000"), false);
        List<ScheduledActivity> schedulerActivities = Lists.newArrayList(act1, act2, act3);
        doReturn(schedulerActivities).when(service).scheduleActivitiesForPlans(any());

        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There should only be 3 activities, and the actGuid3 activity should have selected the db version with state
        assertEquals(3, activities.size());
        assertEquals(1, filterByGuid(activities, "actGuid1").size());
        assertEquals(1, filterByGuid(activities, "actGuid2").size());
        assertEquals(1, filterByGuid(activities, "actGuid3").size());
        assertTrue(filterByGuid(activities, "actGuid3").get(0).getStartedOn() != null);
    }

    @Test
    public void testNothingPersistedScenario() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Duplicated persisted activities.
        List<ScheduledActivity> dbActivities = Lists.newArrayList();
        doReturn(dbActivities).when(activityDao).getActivities(any(), any());
        
        // Correctly scheduled one-time tasks coming from scheduler
        ScheduledActivity act1 = activity(schedule, "actGuid1", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act2 = activity(schedule, "actGuid2", DateTime.parse("2016-06-30T00:00:00.000"), false);
        ScheduledActivity act3 = activity(schedule, "actGuid3", DateTime.parse("2016-06-30T00:00:00.000"), false);
        List<ScheduledActivity> schedulerActivities = Lists.newArrayList(act1, act2, act3);
        doReturn(schedulerActivities).when(service).scheduleActivitiesForPlans(any());

        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There should only be 3 activities, and the actGuid3 activity should have selected the db version with state
        assertEquals(3, activities.size());
        assertEquals(1, filterByGuid(activities, "actGuid1").size());
        assertEquals(1, filterByGuid(activities, "actGuid2").size());
        assertEquals(1, filterByGuid(activities, "actGuid3").size());
    }

    
    private List<ScheduledActivity> filterByGuid(List<ScheduledActivity> activities, String guid) {
        return activities.stream().filter(act -> {
            return act.getGuid().startsWith(guid);
        }).collect(Collectors.toList());
    }
    
    private ScheduledActivity activity(Schedule schedule, String actGuid, DateTime scheduledTime, boolean isFinished) {
        ScheduledActivity act = ScheduledActivity.create();
        act.setSchedule(schedule);
        act.setTimeZone(DateTimeZone.UTC);
        act.setSchedulePlanGuid(SCHEDULE_PLAN_GUID);
        act.setGuid(actGuid + ":" + scheduledTime.toLocalDateTime().toString());
        act.setHealthCode(HEALTH_CODE);
        act.setActivity(new Activity.Builder().withLabel("Activity " + actGuid).withTask("task"+actGuid).withGuid(actGuid).build());
        if (isFinished) {
            act.setStartedOn(ENROLLMENT.getMillis());
        }
        act.setScheduledOn(scheduledTime);
        return act;
    }
       
}
