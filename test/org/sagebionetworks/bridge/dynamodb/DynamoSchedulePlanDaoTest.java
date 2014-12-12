package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    ObjectMapper mapping = BridgeObjectMapper.get();
    
    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    @Resource
    DynamoSurveyDao surveyDao;
    
    @Resource
    StudyServiceImpl studyService;
    
    private Study study;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedulePlan.class);
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class);
        study = studyService.getStudyByIdentifier(TEST_STUDY_IDENTIFIER);
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
        SchedulePlan newPlan = schedulePlanDao.getSchedulePlan(study, abPlan.getGuid());
        assertEquals("Schedule plan contains correct strategy class type", SimpleScheduleStrategy.class, newPlan.getStrategy().getClass());
        
        assertEquals("The strategy has been updated", simplePlan.getStrategy().hashCode(), newPlan.getStrategy().hashCode());
        
        // delete, throws exception
        schedulePlanDao.deleteSchedulePlan(study, newPlan.getGuid());
        try {
            schedulePlanDao.getSchedulePlan(study, newPlan.getGuid());
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
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(study);
        assertEquals("2 plans exist", 2, plans.size());
    }
    
    @Test
    public void canDetectWhenSurveyIsInUseByAPlan() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        Survey survey = new TestSurvey(true);
        survey = surveyDao.createSurvey(survey);
        
        String url = String.format("https://%s/api/v1/surveys/%s/%s",
                config.getStudyHostname(TestConstants.TEST_STUDY_IDENTIFIER), survey.getGuid(),
                DateUtils.convertToISODateTime(survey.getCreatedOn()));
        
        SchedulePlan plan = createASchedulePlan(url);
        
        plan = schedulePlanDao.createSchedulePlan(plan);
        
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlansForSurvey(study, survey);
        assertEquals("There should be one plan returned", 1, plans.size());
        
        try {
            // Should not be able to delete a survey at this opint
            surveyDao.deleteSurvey(study, survey);
            fail("Was able to delete without a problem");
        } catch(IllegalStateException e) {
        }
        
        schedulePlanDao.deleteSchedulePlan(study, plan.getGuid());
        // Now you can delete the survey
        surveyDao.deleteSurvey(study, survey);
        
        // Verify both have been deleted.
        try {
            schedulePlanDao.getSchedulePlan(study, plan.getGuid());
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        try {
            surveyDao.getSurvey(survey);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
    }

    private SchedulePlan createASchedulePlan(String url) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(new Activity("Take this test survey", ActivityType.SURVEY, url));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        return plan;
    }

}
