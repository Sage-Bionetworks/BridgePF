package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.sagebionetworks.bridge.models.accounts.UserConsent;
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
        
        // Mock user consent, we don't care about that, we're just getting an enrollment date from that.
        UserConsent consent = mock(DynamoUserConsent3.class);
        when(consent.getSignedOn()).thenReturn(new DateTime().minusDays(2).getMillis()); 
        
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
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withUser(user)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withEvents(eventMap()).build();
        
        List<ScheduledActivity> activitiesToSchedule = TestUtils.runSchedulerForActivities(user, context);
        activityDao.saveActivities(activitiesToSchedule);
        
        List<ScheduledActivity> activities = activityDao.getActivities(context.getZone(), activitiesToSchedule);
        assertFalse("activities were created", activities.isEmpty());
        
        // Have activities gotten injected time zone? We have to do this during construction using the time zone
        // sent with this call/request.
        assertEquals(DateTimeZone.UTC, ((DynamoScheduledActivity)activities.get(0)).getTimeZone());
        
        // Delete most information in activities and delete one by finishing it. This does not remove it from the 
        // database, however. Its status is just marked FINISHED
        cleanActivities(activities);
        ScheduledActivity activity = activities.get(1);
        activity.setFinishedOn(context.getNow().getMillis());
        assertEquals("activity deleted", ScheduledActivityStatus.DELETED, activity.getStatus());
        activityDao.updateActivities(user.getHealthCode(), Lists.newArrayList(activity));
        
        // Finishing a task does not remove it from the results... nothing now removes items from results except
        // a real physical delete
        List<ScheduledActivity> newActivities = activityDao.getActivities(context.getZone(), activities);
        assertEquals(activities.size(), newActivities.size());
        
        // This is a physical delete however, and they will really be gone.
        activityDao.deleteActivitiesForUser(user.getHealthCode());
        activities = activityDao.getActivities(context.getZone(), activities);
        assertEquals("all activities deleted", 0, activities.size());
    }
    
    @Test
    public void deleteActivities() throws Exception {
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withUser(user)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withEvents(eventMap()).build();
        
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(user, context);

        // just sanity check that we did schedule some tasks before saving them.
        int length = activities.size();
        assertTrue(length > 1);
        activityDao.saveActivities(activities);
        
        // This is a physical delete, they will really be gone from the database.
        activityDao.deleteActivities(activities);
        
        activities = activityDao.getActivities(context.getZone(), activities);
        assertTrue(activities.isEmpty());
    }
    
    @Test
    public void createActivitiesAndDeleteSomeBySchedulePlan() throws Exception {
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withHealthCode(user.getHealthCode())
            .withEvents(eventMap()).build();
        
        // Schedule plans are specific to this test because we're going to delete them;
        List<SchedulePlan> plans = getSchedulePlans();
        SchedulePlan testPlan = plans.get(0);
        SchedulePlan testPlan2 = plans.get(1);
        
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(plans, user, context);
        activityDao.saveActivities(activities);

        // Get one schedule plan GUID to delete and the initial count
        int initialCount = activities.size();
        
        activityDao.deleteActivitiesForSchedulePlan(testPlan.getGuid());
        
        activities = activityDao.getActivities(context.getZone(), activities);
        // The count is now less than before
        assertTrue(initialCount > activities.size());
        // and the supplied schedulePlanGuid cannot be found in any of the activities that still exist
        for (ScheduledActivity activity : activities) {
            assertNotEquals(testPlan.getGuid(), activity.getSchedulePlanGuid());
        }
        
        // Finally, verify that finished activities are not physically deleted. 
        ScheduledActivity finishedActivity = findAnActivityFor(activities, testPlan2);
        finishedActivity.setFinishedOn(DateTime.now().getMillis());
        activityDao.saveActivities(activities);
        
        finishedActivity = activityDao.getActivity(context.getHealthCode(), finishedActivity.getGuid());
        assertNotNull("Finished activity finished, not deleted", finishedActivity);
        assertTrue("Finished activity has finishedOn timestamp", finishedActivity.getFinishedOn() > 0L);
        
        // This deletes everything, however.
        activityDao.deleteActivitiesForSchedulePlan(testPlan2.getGuid());
        List<ScheduledActivity> newActivities = activityDao.getActivities(context.getZone(), activities);
        for (ScheduledActivity newAct : newActivities) {
            assertNotEquals("New activities do not include the deleted plan", testPlan2.getGuid(), newAct.getGuid());
        }
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
    
    private ScheduledActivity findAnActivityFor(List<ScheduledActivity> activities, SchedulePlan plan) {
        for (ScheduledActivity activity : activities) {
            if (activity.getSchedulePlanGuid().equals(plan.getGuid())) {
                return activity;
            }
        }
        throw new IllegalArgumentException("An activity for the schedule plan was not found.");
    }

    private void cleanActivities(List<ScheduledActivity> activities) {
        for (ScheduledActivity schActivity : activities) {
            schActivity.setStartedOn(null);
            schActivity.setFinishedOn(null);
        }
    }
    
}
