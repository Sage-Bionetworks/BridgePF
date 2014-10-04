package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    ObjectMapper mapping = new ObjectMapper();
    
    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    @Before
    public void before() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class, "modifiedOn", "version", "strategy");
    }
    
    @Test
    public void canSerializeAndDeserializeSchedulePlan() throws Exception {
        TestABSchedulePlan abPlan = new TestABSchedulePlan();
        String output = mapping.writeValueAsString(abPlan);
        
        TestABSchedulePlan newPlan = mapping.readValue(output, TestABSchedulePlan.class);
        
        assertEquals("Schedule plans are equal", abPlan.hashCode(), newPlan.hashCode());
    }
    
    @Test
    public void canCrudOneSchedulePlan() {
        TestABSchedulePlan abPlan = new TestABSchedulePlan();
        
        SchedulePlan savedPlan = schedulePlanDao.createSchedulePlan(abPlan);
        assertNotNull("Creates and returns a GUID", abPlan.getGuid());
        assertEquals("GUID is the same", savedPlan.getGuid(), abPlan.getGuid());
        
        // Update the plan... to a simple strategy
        TestSimpleSchedulePlan simplePlan = new TestSimpleSchedulePlan();
        abPlan.setStrategy(simplePlan.getStrategy());
        schedulePlanDao.updateSchedulePlan(abPlan);
        
        // Get it from DynamoDB
        SchedulePlan newPlan = schedulePlanDao.getSchedulePlan(TestConstants.SECOND_STUDY, abPlan.getGuid());
        assertEquals("Schedule plan contains correct strategy class type", SimpleScheduleStrategy.class, newPlan.getStrategy().getClass());
        
        assertEquals("The strategy has been updated", simplePlan.getStrategy().hashCode(), newPlan.getStrategy().hashCode());
        
        // delete, throws exception
        schedulePlanDao.deleteSchedulePlan(TestConstants.SECOND_STUDY, newPlan.getGuid());
        try {
            schedulePlanDao.getSchedulePlan(TestConstants.SECOND_STUDY, newPlan.getGuid());
            fail("Should have thrown an entity not found exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void getAllSchedulePlans() {
        TestABSchedulePlan abPlan = new TestABSchedulePlan();
        TestSimpleSchedulePlan simplePlan = new TestSimpleSchedulePlan();
        
        schedulePlanDao.createSchedulePlan(abPlan);
        schedulePlanDao.createSchedulePlan(simplePlan);
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(TestConstants.SECOND_STUDY);
        assertEquals("2 plans exist", 2, plans.size());
    }

}
