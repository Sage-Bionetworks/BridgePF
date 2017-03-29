package org.sagebionetworks.bridge.dynamodb;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
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

import com.fasterxml.jackson.databind.JsonNode;
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
    
    private String healthCode;
    
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
        schedule.addActivity(TestUtils.getActivity3());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("And this is a schedule plan");
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        
        plan = schedulePlanService.createSchedulePlan(study, plan);

        healthCode = BridgeUtils.generateGuid();
    }
    
    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TEST_STUDY, plan.getGuid());
        activityDao.deleteActivitiesForUser(healthCode);
    }

    @Test
    public void getScheduledActivityHistoryV2() throws Exception {
        // Let's use an interesting time zone so we can verify it is being used.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        // Make a lot of tasks (30 days worth), enough to create a page
        DateTime endsOn = DateTime.now(MSK).plus(Period.parse("P30D"));
        DateTime startDateTime = DateTime.now().minusDays(20);
        DateTime endDateTime = DateTime.now().plusDays(20);
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withHealthCode(healthCode)
            .withStudyIdentifier(TEST_STUDY_IDENTIFIER)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withInitialTimeZone(MSK)
            .withEndsOn(endsOn)
            .withEvents(eventMap()).build();
        
        Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
        List<ScheduledActivity> activitiesToSchedule = schedule.getScheduler().getScheduledActivities(plan, context);
        activityDao.saveActivities(activitiesToSchedule);
        
        String activityGuid = extractActivityGuid();
        
        // Get the first page of 10 records
        ForwardCursorPagedResourceList<ScheduledActivity> history = activityDao.getActivityHistoryV2(
                healthCode, activityGuid, startDateTime, endDateTime, null, 10);
        assertEquals(10, history.getItems().size());
        
        Set<String> allTaskGuids = history.getItems().stream().map(ScheduledActivity::getGuid).collect(toSet());

        // Get second page of records
        history = activityDao.getActivityHistoryV2(
                healthCode, activityGuid, startDateTime, endDateTime, history.getOffsetBy(), 10);
        assertEquals(10, history.getItems().size());

        // Now add the GUIDS of the next ten records to the set
        allTaskGuids.addAll(history.getItems().stream().map(ScheduledActivity::getGuid).collect(toSet()));

        // Should be 20 items in the set (that is, two entirely separate pages of GUIDs)
        assertEquals(20, allTaskGuids.size());
        
        // Query for a time range that will produce no records
        history = activityDao.getActivityHistoryV2(
                healthCode, activityGuid, startDateTime, startDateTime, null, 10);
        assertEquals(0, history.getItems().size());
        assertNull(history.getOffsetBy());
    }
    
    @Test
    public void createUpdateDeleteActivities() throws Exception {
        // Let's use an interesting time zone so we can verify it is being used.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        
        DateTime endsOn = DateTime.now(MSK).plus(Period.parse("P4D"));
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withHealthCode(healthCode)
            .withStudyIdentifier(TEST_STUDY_IDENTIFIER)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withInitialTimeZone(MSK)
            .withEndsOn(endsOn)
            .withEvents(eventMap()).build();
        
        List<ScheduledActivity> activitiesToSchedule = TestUtils.runSchedulerForActivities(context);
        activityDao.saveActivities(activitiesToSchedule);
        
        List<ScheduledActivity> savedActivities = activityDao.getActivities(endsOn.getZone(), activitiesToSchedule);
        
        assertEquals("activities were created", activitiesToSchedule, savedActivities);
        
        // Have activities gotten injected time zone? We have to do this during construction using the time zone
        // sent with this call/request.
        assertEquals(MSK, ((DynamoScheduledActivity)savedActivities.get(0)).getTimeZone());
        
        // Verify getActivity() works
        ScheduledActivity savedActivity = activityDao.getActivity(context.getCriteriaContext().getHealthCode(),
                savedActivities.get(0).getGuid());
        savedActivity.setTimeZone(MSK); // for equality check
        assertEquals(savedActivities.get(0), savedActivity);
        assertEquals(context.getInitialTimeZone(), savedActivity.getTimeZone());
        assertEquals(MSK, savedActivity.getScheduledOn().getZone());
        
        // Create a new list of activities removing some that are saved, and adding new ones.
        List<ScheduledActivity> reducedSet = activitiesToSchedule.subList(0, activitiesToSchedule.size()-2);
        List<ScheduledActivity> anotherSet = TestUtils.runSchedulerForActivities(getSchedulePlans(), context);
        anotherSet.addAll(reducedSet);
        
        // You get back the intersection of activities that have been saved, only.
        List<ScheduledActivity> intersection = activityDao.getActivities(context.getInitialTimeZone(), anotherSet);
        assertEquals(reducedSet, intersection);
        
        // Finish and delete
        JsonNode clientData = TestUtils.getClientData();
        
        // Finish one of the activities, and save some data with it too.
        ScheduledActivity activity = savedActivities.get(1);
        activity.setFinishedOn(context.getNow().getMillis());
        activity.setClientData(clientData);
        assertEquals("activity deleted", ScheduledActivityStatus.DELETED, activity.getStatus());
        activityDao.updateActivities(healthCode, Lists.newArrayList(activity));
        
        // This does not remove it from the database, however.
        List<ScheduledActivity> newActivities = activityDao.getActivities(context.getInitialTimeZone(), savedActivities);
        assertEquals(savedActivities.size(), newActivities.size());
        
        ScheduledActivity activityWithClientData = newActivities.stream()
                .filter(act -> act.getClientData() != null).findFirst().get();
        assertTrue(activityWithClientData.getClientData().get("booleanFlag").asBoolean());
        assertEquals("testString", activityWithClientData.getClientData().get("stringValue").asText());
        assertEquals(4, activityWithClientData.getClientData().get("intValue").asInt());
        
        // This is a physical delete, and the activities will be gone.
        activityDao.deleteActivitiesForUser(healthCode);
        savedActivities = activityDao.getActivities(context.getInitialTimeZone(), savedActivities);
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
        testPlan.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withGuid(BridgeUtils.generateGuid())
                .withLabel("Activity4").withTask("tapTest").build()));
        testPlan.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan);

        SchedulePlan testPlan2 = new DynamoSchedulePlan();
        testPlan2.setGuid(BridgeUtils.generateGuid());
        testPlan2.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withGuid(BridgeUtils.generateGuid())
                .withLabel("Activity5").withTask("anotherTapTest").build()));
        testPlan2.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan2);
        
        SchedulePlan testPlan3 = new DynamoSchedulePlan();
        testPlan3.setGuid(BridgeUtils.generateGuid());
        testPlan3.setStrategy(TestUtils.getStrategy("P2D", new Activity.Builder().withGuid(BridgeUtils.generateGuid())
                .withLabel("Activity6").withTask("yetAnotherTapTest").build()));
        testPlan3.setStudyKey(TEST_STUDY.getIdentifier());
        plans.add(testPlan3);
        
        return plans;
    }

    private String extractActivityGuid() {
        SimpleScheduleStrategy strategy = (SimpleScheduleStrategy) plan.getStrategy();
        return strategy.getAllPossibleSchedules().get(0).getActivities().get(0).getGuid();
    }
}
