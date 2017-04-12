package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ABTestGroup;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SchedulePlanServiceTest {

    @Resource
    SchedulePlanService schedulePlanService;
    
    private Study study;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setTaskIdentifiers(Sets.newHashSet("AAA", "BBB", "CCC"));
    }
        
    @Test
    public void canSaveSchedulePlan() throws Exception {
        SchedulePlan savedPlan = null;
        try {
            SchedulePlan plan = TestUtils.getABTestSchedulePlan(study);
            plan.setGuid(null);
            plan.setVersion(null);
            savedPlan = schedulePlanService.createSchedulePlan(study, plan);
            assertNotNull(savedPlan.getGuid());
            assertNotNull(savedPlan.getVersion());
        } finally {
            schedulePlanService.deleteSchedulePlan(new StudyIdentifierImpl(savedPlan.getStudyKey()), savedPlan.getGuid());
        }
    }
    
    @Test
    public void canRoundTripSchedulePlan() throws Exception {
        SchedulePlan plan = TestUtils.getABTestSchedulePlan(TEST_STUDY);
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        
        SchedulePlan retrieved = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertEquals("Test A/B Schedule", retrieved.getLabel());
        
        // Walk down the object graph and verify that data is there.
        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy)retrieved.getStrategy();
        ABTestGroup group = strategy.getScheduleGroups().get(0);
        Activity activity = group.getSchedule().getActivities().get(0);
        assertEquals("Do AAA task", activity.getLabel());
    }
    
    // Two behaviors here: an activity with no GUID will get one, and an activity with an 
    // unknown GUID (not currently saved) will also be assigned a GUID, to prevent spoofing 
    // or just accidentally breaking the system by assigning a non-unique GUID.
    @Test
    public void updateOfSchedulePlanSetsGuids() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_STUDY);
        plan.setLabel("Label");
        
        List<Activity> activities = Lists.newArrayList(taskActivity("AAA"), taskActivity("BBB"));
        getSchedule(plan).setActivities(activities);
        
        SchedulePlan asSaved = schedulePlanService.createSchedulePlan(study, plan);
        
        // Existing activity, change the GUID to be invalid
        Activity act2 = getSchedule(asSaved).getActivities().get(1);
        act2 = new Activity.Builder().withActivity(act2).withGuid("BAD_GUID").build();
        
        // New activity, has no GUID
        Activity act3 = new Activity.Builder().withActivity(taskActivity("CCC")).withGuid(null).build();
        
        // Add these back to the collection;
        activities.set(1, act2);
        activities.add(act3);
        
        SchedulePlan asUpdated = schedulePlanService.updateSchedulePlan(study, plan);
        
        assertNotEquals("BAD_GUID", getSchedule(asUpdated).getActivities().get(1).getGuid());
        assertNotNull(getSchedule(asUpdated).getActivities().get(2).getGuid());
    }
    
    private Activity taskActivity(String identifier) {
        return new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Activity"+identifier)
                .withTask(identifier).build();        
    }
    
    private Schedule getSchedule(SchedulePlan plan) {
        return plan.getStrategy().getAllPossibleSchedules().get(0);
    }
    
}
