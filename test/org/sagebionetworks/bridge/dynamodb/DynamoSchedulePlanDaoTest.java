package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    private ObjectMapper mapping = BridgeObjectMapper.get();
    
    @Resource
    private DynamoSchedulePlanDao schedulePlanDao;
    
    @Resource
    private DynamoSurveyDao surveyDao;
    
    private StudyIdentifier studyIdentifier;
    
    private Set<SchedulePlan> plansToDelete;
    
    @Before
    public void before() {
        studyIdentifier = new StudyIdentifierImpl(TestUtils.randomName(DynamoSchedulePlanDaoTest.class));
        plansToDelete = Sets.newHashSet();
    }
    
    @After
    public void after() {
        for (SchedulePlan plan : plansToDelete) {
            schedulePlanDao.deleteSchedulePlan(new StudyIdentifierImpl(plan.getStudyKey()), plan.getGuid());
        }
    }
    
    @Test
    public void canSerializeAndDeserializeSchedulePlan() throws Exception {
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(studyIdentifier);
        String output = mapping.writeValueAsString(abPlan);
        
        SchedulePlan newPlan = mapping.readValue(output, SchedulePlan.class);
        
        assertEquals("Schedule plans are equal", abPlan.hashCode(), newPlan.hashCode());
    }
    
    @Test
    public void studySetIfIncorrect() throws Exception {
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(new StudyIdentifierImpl("wrong-study"));
        SchedulePlan savedPlan = schedulePlanDao.createSchedulePlan(new StudyIdentifierImpl("correct1"), abPlan);
        assertEquals("correct1", savedPlan.getStudyKey());
        plansToDelete.add(savedPlan);

        // Passing in a different study key creates a new plan (or throws a ConditionalCheckFailedException if there's 
        // a version for an existing object). It does a create rather than an update, but it still enforces the correct study.
        abPlan = TestUtils.getABTestSchedulePlan(new StudyIdentifierImpl("wrong-study"));
        SchedulePlan nextPlan = schedulePlanDao.updateSchedulePlan(new StudyIdentifierImpl("correct2"), abPlan);
        assertEquals("correct2", nextPlan.getStudyKey());
        plansToDelete.add(nextPlan);
    }
    
    @Test
    public void canCrudOneSchedulePlan() {
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(studyIdentifier);
        
        SchedulePlan savedPlan = schedulePlanDao.createSchedulePlan(studyIdentifier, abPlan);
        assertNotNull("Creates and returns a GUID", abPlan.getGuid());
        assertEquals("GUID is the same", savedPlan.getGuid(), abPlan.getGuid());
        
        // Update the plan... to a simple strategy
        SchedulePlan simplePlan = TestUtils.getSimpleSchedulePlan(studyIdentifier);
        abPlan.setStrategy(simplePlan.getStrategy());
        schedulePlanDao.updateSchedulePlan(studyIdentifier, abPlan);
        
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
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(studyIdentifier);
        SchedulePlan simplePlan = TestUtils.getSimpleSchedulePlan(studyIdentifier);
        
        SchedulePlan plan1 = schedulePlanDao.createSchedulePlan(studyIdentifier, abPlan);
        plansToDelete.add(plan1);
        SchedulePlan plan2 = schedulePlanDao.createSchedulePlan(studyIdentifier, simplePlan);
        plansToDelete.add(plan2);
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyIdentifier);
        assertEquals(getSchedulePlanGuids(plan1, plan2), getSchedulePlanGuids(plans));
    }
    
    @Test
    public void filtersSchedulePlans() {
        Set<String> guids = Sets.newHashSet();
        Set<String> oneGuid = Sets.newHashSet();
        
        List<SchedulePlan> plans = TestUtils.getSchedulePlans(studyIdentifier);
        SchedulePlan plan = schedulePlanDao.createSchedulePlan(studyIdentifier, plans.get(0));
        guids.add(plan.getGuid());
        plansToDelete.add(plan);
        
        SchedulePlan plan2 = schedulePlanDao.createSchedulePlan(studyIdentifier, plans.get(1));
        guids.add(plan2.getGuid());
        oneGuid.add(plan2.getGuid());
        plansToDelete.add(plan2);

        SchedulePlan plan3 = schedulePlanDao.createSchedulePlan(studyIdentifier, plans.get(2));
        guids.add(plan3.getGuid());
        plansToDelete.add(plan3);
        
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
