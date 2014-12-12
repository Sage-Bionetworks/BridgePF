package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy.ScheduleGroup;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SchedulePlanServiceTest {

    @Resource
    SchedulePlanServiceImpl schedulePlanService;
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void cannotCreateExistingSchedulePlan() {
        TestABSchedulePlan plan = new TestABSchedulePlan();
        schedulePlanService.createSchedulePlan(plan);
    }
    
    @Test
    public void canRoundTripSchedulePlan() throws Exception {
        TestABSchedulePlan plan = new TestABSchedulePlan();
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        
        SchedulePlan retrieved = BridgeObjectMapper.get().readValue(json, DynamoSchedulePlan.class);

        // Walk down the object graph and verify that data is there.
        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy)retrieved.getStrategy();
        ScheduleGroup group = strategy.getScheduleGroups().get(0);
        Activity activity = group.getSchedule().getActivities().get(0);
        assertEquals("Do AAA task", activity.getLabel());
    }
}
