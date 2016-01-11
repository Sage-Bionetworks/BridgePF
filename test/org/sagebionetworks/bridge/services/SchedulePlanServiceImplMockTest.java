package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Sets;

public class SchedulePlanServiceImplMockTest {

    private SchedulePlanServiceImpl service;
    private SchedulePlanDao schedulePlanDao;
    private SurveyService surveyService;
    private ScheduledActivityService activityService;
    private Study study;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setDataGroups(Sets.newHashSet("AAA"));
        study.setTaskIdentifiers(Sets.newHashSet("CCC"));
        
        service = new SchedulePlanServiceImpl();
        
        schedulePlanDao = mock(SchedulePlanDao.class);
        surveyService = mock(SurveyService.class);
        activityService = mock(ScheduledActivityService.class);
        
        service.setSchedulePlanDao(schedulePlanDao);
        service.setSurveyService(surveyService);
        service.setScheduledActivityService(activityService);
    }
    
    @Test
    public void schedulePlanSetsStudyIdentifierOnCreate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = getSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(schedulePlanDao.createSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.createSchedulePlan(anotherStudy, plan);
        assertEquals("another-study", plan.getStudyKey());
    }
    
    @Test
    public void schedulePlanSetsStudyIdentifierOnUpdate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = getSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(schedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.updateSchedulePlan(anotherStudy, plan);
        assertEquals("another-study", plan.getStudyKey());
    }
    
    @Test
    public void cleansUpScheduledActivitiesOnUpdate() {
        SchedulePlan plan = getSchedulePlan();
        when(schedulePlanDao.updateSchedulePlan(study.getStudyIdentifier(), plan)).thenReturn(plan);
        
        service.updateSchedulePlan(study, plan);
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
    
    @Test
    public void cleansUpScheduledActivitiesOnDelete() {
        service.deleteSchedulePlan(TEST_STUDY, "BBB");
        
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
    
    @Test
    public void validatesOnCreate() {
        // Check that 1) validation is called and 2) the study's enumerations are used in the validation
        SchedulePlan plan = createInvalidSchedulePlan();
        try {
            service.createSchedulePlan(study, plan);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: CCC.", e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0));
            assertEquals("strategy.scheduleCriteria[0].allOfGroups 'FFF' is not in enumeration: AAA", e.getErrors().get("strategy.scheduleCriteria[0].allOfGroups").get(0));
        }
    }
    
    @Test
    public void validatesOnUpdate() {
        // Check that 1) validation is called and 2) the study's enumerations are used in the validation
        SchedulePlan plan = createInvalidSchedulePlan();
        try {
            service.updateSchedulePlan(study, plan);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: CCC.", e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0));
            assertEquals("strategy.scheduleCriteria[0].allOfGroups 'FFF' is not in enumeration: AAA", e.getErrors().get("strategy.scheduleCriteria[0].allOfGroups").get(0));
        }
    }
    
    private SchedulePlan createInvalidSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withTask("DDD").build());
        
        ScheduleCriteria criteria = new ScheduleCriteria.Builder()
                .withSchedule(schedule)
                .addRequiredGroup("FFF")
                .build();
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        strategy.addCriteria(criteria);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setStrategy(strategy);
        return plan;
    }
    
    private DynamoStudy getAnotherStudy() {
        DynamoStudy anotherStudy = new DynamoStudy();
        anotherStudy.setIdentifier("another-study");
        anotherStudy.setTaskIdentifiers(Sets.newHashSet("CCC"));
        return anotherStudy;
    }
    
    private SchedulePlan getSchedulePlan() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_STUDY);
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.getStrategy().getAllPossibleSchedules().get(0).setExpires("P3D");
        return plan;
    }
}

