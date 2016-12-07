package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.SurveyService;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class OneTimeShortDurationTaskTest {
    
    // Or: 2016-12-06T18:35:14.116-08:00
    private static final DateTime REQUESTED_ON = DateTime.parse("2016-12-07T02:35:14.116Z");
    private static final String EVENT_ID = "activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished";
    private static final DateTimeZone PST_ZONE = DateTimeZone.forOffsetHours(-8);
    private static final DateTime SCHEDULED_ON = DateTime.parse("2016-12-06T20:34:14.116-08:00");
    private static final DateTime EXPIRES_ON = DateTime.parse("2016-12-06T21:34:14.116-08:00");

    @Mock
    private SchedulePlanService schedulePlanService;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private SurveyService surveyService;
    
    @Captor
    private ArgumentCaptor<List<ScheduledActivity>> schActivitiesCaptor;
    
    private ScheduleContext context;
    
    private ScheduledActivityService service;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(REQUESTED_ON.getMillis());
        service = new ScheduledActivityService();
        service.setSchedulePlanService(schedulePlanService);
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSurveyService(surveyService);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private ScheduledActivity createScheduleActivity(Schedule schedule) {
        DynamoScheduledActivity act = new DynamoScheduledActivity();
        act.setHealthCode("BBB");
        act.setGuid("3ebaf94a-c797-4e9c-a0cf-4723bbf52102:2016-12-06T20:34:14.116");
        act.setSchedulePlanGuid("schedulePlanGuid");
        act.setTimeZone(PST_ZONE);
        act.setActivity(schedule.getActivities().get(0));
        act.setSchedule(schedule);
        act.setLocalScheduledOn(SCHEDULED_ON.toLocalDateTime());
        act.setLocalExpiresOn(EXPIRES_ON.toLocalDateTime());
        return act;
    }
    
    @Test
    public void test() {
        System.out.println(DateTimeZone.forOffsetHours(-7).toString());
    }
    
    // This verifies a fix for a situation that was known to fail. We set the time to 
    // midnight, so it's long expired by the time of the request, given its short lifetime. 
    @Test
    public void oneTimeShortExpirationTaskPersisted() {
        setupTest(true);
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        for (ScheduledActivity act : activities) {
            System.out.println(act);
        }
        
        verify(schedulePlanService).getSchedulePlans(any(), any());
        verify(activityDao).getActivities(any(), any());
        verify(activityEventService).getActivityEventMap(any());
        verify(activityDao).saveActivities(schActivitiesCaptor.capture());
        
        assertTrue(schActivitiesCaptor.getValue().isEmpty());
        assertEquals(1, activities.size());
        assertEquals(SCHEDULED_ON, activities.get(0).getScheduledOn());
        assertEquals(EXPIRES_ON, activities.get(0).getExpiresOn());
    }
    
    @Test
    public void oneTimeShortExpirationTaskNotPersisted() {
        setupTest(false);
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        verify(schedulePlanService).getSchedulePlans(any(), any());
        verify(activityDao).getActivities(any(), any());
        verify(activityEventService).getActivityEventMap(any());
        verify(activityDao).saveActivities(schActivitiesCaptor.capture());
        
        assertEquals(1, schActivitiesCaptor.getValue().size());
        assertEquals(SCHEDULED_ON, schActivitiesCaptor.getValue().get(0).getScheduledOn());
        assertEquals(EXPIRES_ON, schActivitiesCaptor.getValue().get(0).getExpiresOn());
        
        assertEquals(1, activities.size());
        assertEquals(SCHEDULED_ON, activities.get(0).getScheduledOn());
        assertEquals(EXPIRES_ON, activities.get(0).getExpiresOn());
    }
    
    private void setupTest(boolean taskHasBeenPersisted) {
        Activity activity = new Activity.Builder().withLabel("Activity Session 2")
                .withLabelDetail("Do in clinic - 5 minutes").withGuid("3ebaf94a-c797-4e9c-a0cf-4723bbf52102")
                .withTask("1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780").build();
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId(EVENT_ID);
        schedule.setDelay(Period.parse("PT2H"));
        schedule.setExpires(Period.parse("PT1H"));
        schedule.setActivities(Lists.newArrayList(activity));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Schedule plan");
        plan.setStudyKey("study-key");
        plan.setGuid("schedulePlanGuid");
        plan.setStrategy(strategy);

        doReturn(Lists.newArrayList(plan)).when(schedulePlanService).getSchedulePlans(any(), any());
        
        // Also, return this task from the database, to verify that the service code does not
        // change it to midnight.
        if (taskHasBeenPersisted) {
            ScheduledActivity schActivity = createScheduleActivity(schedule);
            doReturn(Lists.newArrayList(schActivity)).when(activityDao).getActivities(any(), any());
        } else {
            doReturn(Lists.newArrayList()).when(activityDao).getActivities(any(), any());
        }
        
        DateTime endsOn = DateTime.now(PST_ZONE).plusDays(3).withHourOfDay(23).withMinuteOfHour(59)
                .withSecondOfMinute(59);
        
        Map<String,DateTime> events = Maps.newHashMap();
        events.put(EVENT_ID, DateTime.now().minusMinutes(1));
        doReturn(events).when(activityEventService).getActivityEventMap(any());
        
        context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("Lilly/25 (iPhone 6S; iPhone OS/10.1.1) BridgeSDK/12"))
                .withHealthCode("BBB")
                .withAccountCreatedOn(DateTime.now().minusHours(4))
                .withEndsOn(endsOn)
                .withUserId("AAA")
                .withStudyIdentifier("study-key")
                .withTimeZone(PST_ZONE)
                .build();
    }
    
}
