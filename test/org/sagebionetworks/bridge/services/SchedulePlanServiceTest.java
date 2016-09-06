package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ABTestGroup;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
    
}
