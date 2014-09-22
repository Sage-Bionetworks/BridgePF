package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    @Before
    public void before() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class, "modifiedOn", "version", "strategyType", "data");
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(TestConstants.SECOND_STUDY);
        for (SchedulePlan plan : plans) {
            schedulePlanDao.deleteSchedulePlan(TestConstants.SECOND_STUDY, plan.getGuid());
        }
    }
    
    @Test
    public void canCrudOneSchedulePlan() {
        TestABSchedulePlan abPlan = new TestABSchedulePlan();
        
        GuidHolder holder = schedulePlanDao.createSchedulePlan(abPlan);
        assertNotNull("Creates and returns a GUID", abPlan.getGuid());
        assertEquals("GUID is the same", holder.getGuid(), abPlan.getGuid());
        
        // Update the plan... to a simple strategy
        TestSimpleSchedulePlan simplePlan = new TestSimpleSchedulePlan();
        
        abPlan.setScheduleStrategy(simplePlan.getScheduleStrategy());
        System.out.println("AB Test plan: " + abPlan.toString()); // this should have changed type as well. That's crucial.
        
        schedulePlanDao.updateSchedulePlan(abPlan);
        
        SchedulePlan newPlan = schedulePlanDao.getSchedulePlan(TestConstants.SECOND_STUDY, abPlan.getGuid());
        
        System.out.println("New plan: " + newPlan.toString()); // this should have changed type as well. That's crucial.
        
        assertEquals("The strategy has been updated", simplePlan.getScheduleStrategy(), newPlan.getScheduleStrategy());
    }

}
