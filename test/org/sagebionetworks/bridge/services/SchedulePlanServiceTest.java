package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy.ScheduleGroup;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SchedulePlanServiceTest {

    @Resource
    SchedulePlanServiceImpl schedulePlanService;
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void cannotCreateExistingSchedulePlan() {
        SchedulePlan plan = TestABSchedulePlan.create();
        // It's rejected because it has a GUID... this doesn't even get to DDB.
        schedulePlanService.createSchedulePlan(plan);
    }
    
    @Test
    public void schedulePlansAreFilteredByAppVersion() {
        StudyIdentifier studyId = new StudyIdentifierImpl("test-study");
        SchedulePlan savedPlan = null;
        try {
            SchedulePlan plan = TestABSchedulePlan.create();
            plan.setGuid(null);
            plan.setVersion(null);
            plan.setMinAppVersion(14);
            plan.setMaxAppVersion(15);
            plan.setStudyKey(studyId.getIdentifier());
            savedPlan = schedulePlanService.createSchedulePlan(plan);
            
            int baseCount = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyId).size();
            
            // This is not high enough for the app version, nothing returned.
            ClientInfo clientInfo = ClientInfo.fromUserAgentCache("TestApp/13 TestSDK/8");
            List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(clientInfo, studyId);
            assertEquals(baseCount-1, plans.size());
            
            // Right in the range, the plan is returned
            clientInfo = ClientInfo.fromUserAgentCache("TestApp/14 TestSDK/8");
            plans = schedulePlanService.getSchedulePlans(clientInfo, new StudyIdentifierImpl("test-study"));
            assertEquals(baseCount, plans.size());
            
            clientInfo = ClientInfo.fromUserAgentCache("TestApp/15 TestSDK/8");
            plans = schedulePlanService.getSchedulePlans(clientInfo, new StudyIdentifierImpl("test-study"));
            assertEquals(baseCount, plans.size());
            
            // Finally, this is outside the upper-bound, so the plan will not be returned
            clientInfo = ClientInfo.fromUserAgentCache("TestApp/16 TestSDK/40");
            plans = schedulePlanService.getSchedulePlans(clientInfo, new StudyIdentifierImpl("test-study"));
            assertEquals(baseCount-1, plans.size());
            
        } finally {
            schedulePlanService.deleteSchedulePlan(new StudyIdentifierImpl(savedPlan.getStudyKey()), savedPlan.getGuid());
        }
    }
    
    @Test
    public void canSaveSchedulePlan() throws Exception {
        SchedulePlan savedPlan = null;
        try {
            SchedulePlan plan = TestABSchedulePlan.create();
            plan.setGuid(null);
            plan.setVersion(null);
            savedPlan = schedulePlanService.createSchedulePlan(plan);
            assertNotNull(savedPlan.getGuid());
            assertNotNull(savedPlan.getVersion());
        } finally {
            schedulePlanService.deleteSchedulePlan(new StudyIdentifierImpl(savedPlan.getStudyKey()), savedPlan.getGuid());
        }
    }
    
    @Test
    public void canRoundTripSchedulePlan() throws Exception {
        TestABSchedulePlan plan = new TestABSchedulePlan();
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        
        SchedulePlan retrieved = BridgeObjectMapper.get().readValue(json, DynamoSchedulePlan.class);
        assertEquals("Test A/B Schedule", retrieved.getLabel());
        
        // Walk down the object graph and verify that data is there.
        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy)retrieved.getStrategy();
        ScheduleGroup group = strategy.getScheduleGroups().get(0);
        Activity activity = group.getSchedule().getActivities().get(0);
        assertEquals("Do AAA task", activity.getLabel());
    }
    
}
