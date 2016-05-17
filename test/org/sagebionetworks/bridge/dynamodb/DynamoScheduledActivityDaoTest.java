package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

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
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
        Study study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setTaskIdentifiers(Sets.newHashSet("tapTest"));
        
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
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        
        plan = schedulePlanService.createSchedulePlan(study, plan);

        String healthCode = BridgeUtils.generateGuid();

        user = new User();
        user.setHealthCode(healthCode);
        user.setStudyKey(TEST_STUDY_IDENTIFIER);
    }
    
    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TEST_STUDY, plan.getGuid());
        activityDao.deleteActivitiesForUser(user.getHealthCode());
    }

    @Test
    public void createUpdateDeleteActivities() throws Exception {
        // Let's use an interesting time zone so we can verify it is being used.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withHealthCode(user.getHealthCode())
            .withStudyIdentifier(TEST_STUDY_IDENTIFIER)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(MSK)
            .withEndsOn(endsOn)
            .withEvents(eventMap()).build();
        
        List<ScheduledActivity> activitiesToSchedule = TestUtils.runSchedulerForActivities(context);
        activityDao.saveActivities(activitiesToSchedule);
        
        List<ScheduledActivity> savedActivities = activityDao.getActivities(context.getZone(), activitiesToSchedule);
        
        assertEquals("activities were created", activitiesToSchedule, savedActivities);
        
        // Have activities gotten injected time zone? We have to do this during construction using the time zone
        // sent with this call/request.
        assertEquals(MSK, ((DynamoScheduledActivity)savedActivities.get(0)).getTimeZone());
        
        // Verify getActivity() works
        ScheduledActivity savedActivity = activityDao.getActivity(context.getZone(),
                context.getCriteriaContext().getHealthCode(), savedActivities.get(0).getGuid());
        assertEquals(savedActivities.get(0), savedActivity);
        assertEquals(context.getZone(), savedActivity.getTimeZone());
        assertEquals(MSK, savedActivity.getScheduledOn().getZone());
        
        // Create a new list of activities removing some that are saved, and adding new ones.
        List<ScheduledActivity> reducedSet = activitiesToSchedule.subList(0, activitiesToSchedule.size()-2);
        List<ScheduledActivity> anotherSet = TestUtils.runSchedulerForActivities(getSchedulePlans(), context);
        anotherSet.addAll(reducedSet);
        
        // You get back the intersection of activities that have been saved, only.
        List<ScheduledActivity> intersection = activityDao.getActivities(context.getZone(), anotherSet);
        assertEquals(reducedSet, intersection);
        
        // Finish and delete
        
        // Finish one of the activities.  
        ScheduledActivity activity = savedActivities.get(1);
        activity.setFinishedOn(context.getNow().getMillis());
        assertEquals("activity deleted", ScheduledActivityStatus.DELETED, activity.getStatus());
        activityDao.updateActivities(user.getHealthCode(), Lists.newArrayList(activity));
        
        // This does not remove it from the database, however.
        List<ScheduledActivity> newActivities = activityDao.getActivities(context.getZone(), savedActivities);
        assertEquals(savedActivities.size(), newActivities.size());
        
        // This is a physical delete, and the activities will be gone.
        activityDao.deleteActivitiesForUser(user.getHealthCode());
        savedActivities = activityDao.getActivities(context.getZone(), savedActivities);
        assertEquals("all activities deleted", 0, savedActivities.size());
    }
    
    private Map<String,DateTime> eventMap() {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        return events;
    }
    
    private List<SchedulePlan> getSchedulePlans() {
        List<SchedulePlan> plans = Lists.newArrayList();
        SchedulePlan testPlan = new DynamoSchedulePlan();
        testPlan.setGuid(BridgeUtils.generateGuid());
        testPlan.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withLabel("Activity4")
                .withTask("tapTest").build()));
        testPlan.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan);

        SchedulePlan testPlan2 = new DynamoSchedulePlan();
        testPlan2.setGuid(BridgeUtils.generateGuid());
        testPlan2.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withLabel("Activity5")
                .withTask("anotherTapTest").build()));
        testPlan2.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan2);
        
        SchedulePlan testPlan3 = new DynamoSchedulePlan();
        testPlan3.setGuid(BridgeUtils.generateGuid());
        testPlan3.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withLabel("Activity6")
                .withTask("yetAnotherTapTest").build()));
        testPlan3.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan3);
        
        return plans;
    }
    
}
