package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.SurveyService;

import com.google.common.collect.Maps;
import com.newrelic.agent.deps.com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class OneTimeShortExpirationTaskTest {
    
    private static final String EVENT_ID = "activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished";

    @Mock
    private SchedulePlanService schedulePlanService;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private SurveyService surveyService;
    
    private ScheduledActivityService service;
    
    @Before
    public void before() {
        service = new ScheduledActivityService();
        service.setSchedulePlanService(schedulePlanService);
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSurveyService(surveyService);
    }
    
    // This is currently known to fail.
    @Test
    @Ignore
    public void oneTimeShortExpirationTask() {
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
        plan.setStrategy(strategy);

        doReturn(Lists.newArrayList(plan)).when(schedulePlanService).getSchedulePlans(any(), any());
        
        DateTimeZone zone = DateTimeZone.forOffsetHours(-8);
        DateTime endsOn = DateTime.now(zone).plusDays(3).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        
        Map<String,DateTime> events = Maps.newHashMap();
        events.put(EVENT_ID, DateTime.now().minusMinutes(1));
        doReturn(events).when(activityEventService).getActivityEventMap(any());
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("Lilly/25 (iPhone 6S; iPhone OS/10.1.1) BridgeSDK/12"))
                .withHealthCode("BBB")
                .withAccountCreatedOn(DateTime.now().minusHours(4))
                .withEndsOn(endsOn)
                .withUserId("AAA")
                .withStudyIdentifier("study-key")
                .withTimeZone(zone)
                .build();
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        assertEquals(1, activities.size());
    }
}
