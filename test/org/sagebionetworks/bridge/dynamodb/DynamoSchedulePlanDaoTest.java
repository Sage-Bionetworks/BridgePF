package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    ObjectMapper mapping = BridgeObjectMapper.get();
    
    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    @Resource
    DynamoSurveyDao surveyDao;
    
    private StudyIdentifier studyIdentifier;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedulePlan.class);
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class);
        studyIdentifier = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void canSerializeAndDeserializeSchedulePlan() throws Exception {
        SchedulePlan abPlan = TestABSchedulePlan.create();
        String output = mapping.writeValueAsString(abPlan);
        
        SchedulePlan newPlan = mapping.readValue(output, SchedulePlan.class);
        
        assertEquals("Schedule plans are equal", abPlan.hashCode(), newPlan.hashCode());
    }
    
    @Test
    public void canCrudOneSchedulePlan() {
        SchedulePlan abPlan = TestABSchedulePlan.create();
        
        SchedulePlan savedPlan = schedulePlanDao.createSchedulePlan(abPlan);
        assertNotNull("Creates and returns a GUID", abPlan.getGuid());
        assertEquals("GUID is the same", savedPlan.getGuid(), abPlan.getGuid());
        
        // Update the plan... to a simple strategy
        SchedulePlan simplePlan = TestSimpleSchedulePlan.create();
        abPlan.setStrategy(simplePlan.getStrategy());
        schedulePlanDao.updateSchedulePlan(abPlan);
        
        // Get it from DynamoDB
        SchedulePlan newPlan = schedulePlanDao.getSchedulePlan(studyIdentifier, abPlan.getGuid());
        assertEquals("Schedule plan contains correct strategy class type", SimpleScheduleStrategy.class, newPlan.getStrategy().getClass());
        
        assertEquals("The strategy has been updated", simplePlan.getStrategy().hashCode(), newPlan.getStrategy().hashCode());
        
        // delete, throws exception
        schedulePlanDao.deleteSchedulePlan(studyIdentifier, newPlan.getGuid());
        try {
            schedulePlanDao.getSchedulePlan(studyIdentifier, newPlan.getGuid());
            fail("Should have thrown an entity not found exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void getAllSchedulePlans() {
        SchedulePlan abPlan = TestABSchedulePlan.create();
        SchedulePlan simplePlan = TestSimpleSchedulePlan.create();
        
        SchedulePlan plan1 = schedulePlanDao.createSchedulePlan(abPlan);
        SchedulePlan plan2 = schedulePlanDao.createSchedulePlan(simplePlan);
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyIdentifier);
        assertEquals(getSchedulePlanGuids(plan1, plan2), getSchedulePlanGuids(plans));
    }
    
    @Test
    public void filtersSchedulePlans() {
        Set<String> guids = Sets.newHashSet();
        Set<String> oneGuid = Sets.newHashSet();
        
        List<SchedulePlan> plans = TestUtils.getSchedulePlans();
        SchedulePlan plan = schedulePlanDao.createSchedulePlan(plans.get(0));
        guids.add(plan.getGuid());
        
        plan = schedulePlanDao.createSchedulePlan(plans.get(1));
        guids.add(plan.getGuid());
        oneGuid.add(plan.getGuid());

        plan = schedulePlanDao.createSchedulePlan(plans.get(2));
        guids.add(plan.getGuid());
        
        // No known client, all the guids are returned
        plans = schedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyIdentifier);
        assertEquals(guids, getSchedulePlanGuids(plans));
        
        // Only one schedule plan matches v9
        plans = schedulePlanDao.getSchedulePlans(ClientInfo.fromUserAgentCache("app/9"), studyIdentifier);
        assertEquals(oneGuid, getSchedulePlanGuids(plans));
    }

    
    private Set<String> getSchedulePlanGuids(SchedulePlan... plans) {
        Set<String> set = Sets.newHashSet();
        for (SchedulePlan plan : plans) {
            set.add(plan.getGuid());
        }
        return set;
    }
    
    private Set<String> getSchedulePlanGuids(List<SchedulePlan> plans) {
        Set<String> set = Sets.newHashSet();
        for (SchedulePlan plan : plans) {
            set.add(plan.getGuid());
        }
        return set;
    }
}
