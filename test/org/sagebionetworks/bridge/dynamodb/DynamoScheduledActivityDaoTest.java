package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoScheduledActivityDaoTest {
    
    @Resource
    DynamoScheduledActivityDao activityDao;

    @Resource
    SchedulePlanService schedulePlanService;
    
    private SchedulePlan plan;
    
    private User user;
    
    @Before
    public void before() {
        Schedule schedule = new Schedule();
        schedule.setLabel("This is a schedule");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("PT6H");
        schedule.addTimes("10:00", "14:00");
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("And this is a schedule plan");
        plan.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        
        plan = schedulePlanService.createSchedulePlan(plan);

        String healthCode = BridgeUtils.generateGuid();
        
        // Mock user consent, we don't care about that, we're just getting an enrollment date from that.
        UserConsent consent = mock(DynamoUserConsent2.class);
        when(consent.getSignedOn()).thenReturn(new DateTime().minusDays(2).getMillis()); 
        
        user = new User();
        user.setHealthCode(healthCode);
        user.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TestConstants.TEST_STUDY, plan.getGuid());
        activityDao.deleteActivities(user.getHealthCode());
    }

    @Test
    public void createUpdateDeleteActivities() throws Exception {
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withHealthCode(user.getHealthCode())
            .withEvents(events).build();
        
        List<ScheduledActivity> activitiesToSchedule = TestUtils.runSchedulerForActivities(user, context);
        activityDao.saveActivities(activitiesToSchedule);
        
        List<ScheduledActivity> activities = activityDao.getActivities(context);
        int collectionSize = activities.size();
        assertFalse("activities were created", activities.isEmpty());
        
        // Should not increase the number of activities
        activities = activityDao.getActivities(context);
        assertEquals("activities did not grow afer repeated getActivity()", collectionSize, activities.size());

        // Have activities gotten injected time zone? We have to do this during construction using the time zone
        // sent with this call/request.
        assertEquals(DateTimeZone.UTC, ((DynamoScheduledActivity)activities.get(0)).getTimeZone());
        
        // Delete most information in activities and delete one by finishing it
        cleanActivities(activities);
        ScheduledActivity activity = activities.get(1);
        activity.setFinishedOn(context.getNow().getMillis());
        // This logic is now in the service, but essential for the activity to be "deleted"
        activity.setHidesOn(context.getNow().getMillis());
        assertEquals("activity deleted", ScheduledActivityStatus.DELETED, activity.getStatus());
        activityDao.updateActivities(user.getHealthCode(), Lists.newArrayList(activity));
        
        activities = activityDao.getActivities(context);
        assertEquals("deleted activity not returned from server", collectionSize-1, activities.size());
        activityDao.deleteActivities(user.getHealthCode());
        
        activities = activityDao.getActivities(context);
        assertEquals("all activities deleted", 0, activities.size());
    }

    private void cleanActivities(List<ScheduledActivity> activities) {
        for (ScheduledActivity schActivity : activities) {
            schActivity.setStartedOn(null);
            schActivity.setFinishedOn(null);
        }
    }
    
}
