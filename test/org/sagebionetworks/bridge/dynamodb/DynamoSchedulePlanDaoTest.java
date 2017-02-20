package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
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
    
    @Resource
    private CriteriaDao criteriaDao;
    
    private StudyIdentifier studyIdentifier;
    
    private Set<Keys> plansToDelete;
    
    private static final class Keys {
        public final String studyIdentifier;
        public final String guid;
        public Keys(String studyIdentifier, String guid) {
            this.studyIdentifier = studyIdentifier;
            this.guid = guid;
        }
    }
    
    @Before
    public void before() {
        studyIdentifier = new StudyIdentifierImpl(TestUtils.randomName(DynamoSchedulePlanDaoTest.class));
        plansToDelete = Sets.newHashSet();
    }
    
    @After
    public void after() {
        for (Keys keys : plansToDelete) {
            schedulePlanDao.deleteSchedulePlan(new StudyIdentifierImpl(keys.studyIdentifier), keys.guid);
        }
    }
    
    @Test
    public void canSerializeAndDeserializeSchedulePlan() throws Exception {
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(studyIdentifier);
        String output = mapping.writeValueAsString(abPlan);
        
        SchedulePlan newPlan = mapping.readValue(output, SchedulePlan.class);
        newPlan.setStudyKey(abPlan.getStudyKey()); // not serialized
        
        assertEquals("Schedule plans are equal", abPlan, newPlan);
    }
    
    @Test
    public void studySetIfIncorrect() throws Exception {
        SchedulePlan abPlan = TestUtils.getABTestSchedulePlan(new StudyIdentifierImpl("wrong-study"));
        SchedulePlan savedPlan = schedulePlanDao.createSchedulePlan(new StudyIdentifierImpl("correct1"), abPlan);
        assertEquals("correct1", savedPlan.getStudyKey());
        plansToDelete.add(new Keys(savedPlan.getStudyKey(), savedPlan.getGuid()));

        // Passing in a different study key creates a new plan (or throws a ConditionalCheckFailedException if there's 
        // a version for an existing object). It does a create rather than an update, but it still enforces the correct study.
        abPlan = TestUtils.getABTestSchedulePlan(new StudyIdentifierImpl("wrong-study"));
        SchedulePlan nextPlan = schedulePlanDao.updateSchedulePlan(new StudyIdentifierImpl("correct2"), abPlan);
        assertEquals("correct2", nextPlan.getStudyKey());
        plansToDelete.add(new Keys(nextPlan.getStudyKey(), nextPlan.getGuid()));
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
        assertEquals("The strategy has been updated", simplePlan.getStrategy(), newPlan.getStrategy());
        
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
        plansToDelete.add(new Keys(plan1.getStudyKey(), plan1.getGuid()));
        SchedulePlan plan2 = schedulePlanDao.createSchedulePlan(studyIdentifier, simplePlan);
        plansToDelete.add(new Keys(plan2.getStudyKey(), plan2.getGuid()));
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyIdentifier);
        assertEquals(getSchedulePlanGuids(plan1, plan2), getSchedulePlanGuids(plans));
    }
    
    @Test
    public void copySchedulePlan() throws Exception {
        SchedulePlan simplePlan = TestUtils.getSimpleSchedulePlan(studyIdentifier);
        
        SchedulePlan plan1 = schedulePlanDao.createSchedulePlan(studyIdentifier, simplePlan);
        plansToDelete.add(new Keys(plan1.getStudyKey(), plan1.getGuid()));
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan1);
        SchedulePlan plan2 = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);        
        plan2.setStudyKey(plan1.getStudyKey());
        
        SchedulePlan copy = schedulePlanDao.createSchedulePlan(studyIdentifier, plan2);
        plansToDelete.add(new Keys(copy.getStudyKey(), copy.getGuid()));
        
        assertNotEquals(plan1.getGuid(), copy.getGuid());
        assertNotEquals(plan1.getModifiedOn(), copy.getModifiedOn());
        assertEquals((Long)1L, plan1.getVersion());
        assertEquals((Long)1L, copy.getVersion());
    }

    @Test
    public void scheduleCriteriaStrategyWork() {
        StudyIdentifier studyId = new StudyIdentifierImpl("test-study");
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Criteria strategy plan");
        plan.setStudyKey(studyId.getIdentifier());
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_1_ACTIVITY);
        
        Criteria criteria = TestUtils.createCriteria(2, 8, null, null);
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.addCriteria(scheduleCriteria);
        
        criteria = TestUtils.createCriteria(9, 12, null, null); 
        
        schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_2_ACTIVITY);
        scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.addCriteria(scheduleCriteria);
        
        plan.setStrategy(strategy);
        plan = schedulePlanDao.createSchedulePlan(studyId, plan);

        criteria = TestUtils.createCriteria(9, 14, null, null);
        
        schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().set(1, scheduleCriteria);
        
        plan = schedulePlanDao.updateSchedulePlan(studyId, plan);
        
        // Should be able to read the criteria objects for this.
        Criteria criteria1 = criteriaDao.getCriteria("scheduleCriteria:"+plan.getGuid()+":0");
        assertEquals(new Integer(2), criteria1.getMinAppVersion(IOS));
        assertEquals(new Integer(8), criteria1.getMaxAppVersion(IOS));
        
        Criteria criteria2 = criteriaDao.getCriteria("scheduleCriteria:"+plan.getGuid()+":1");
        assertEquals(new Integer(9), criteria2.getMinAppVersion(IOS));
        assertEquals(new Integer(14), criteria2.getMaxAppVersion(IOS));
        
        plansToDelete.add(new Keys(studyId.getIdentifier(), plan.getGuid()));
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
