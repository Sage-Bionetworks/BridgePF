package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.google.common.collect.Sets;

public class SchedulePlanServiceMockTest {

    private Study study;
    private String surveyGuid1;
    private String surveyGuid2;
    private SchedulePlanService service;
    
    private SchedulePlanDao mockSchedulePlanDao;
    private SurveyService mockSurveyService;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setTaskIdentifiers(Sets.newHashSet("tapTest", "taskGuid", "CCC"));
        study.setDataGroups(Sets.newHashSet("AAA"));
        
        mockSchedulePlanDao = mock(SchedulePlanDao.class);
        mockSurveyService = mock(SurveyService.class);
        
        service = new SchedulePlanService();
        service.setSchedulePlanDao(mockSchedulePlanDao);
        service.setSurveyService(mockSurveyService);
        
        Survey survey1 = TestUtils.getSurvey(false);
        survey1.setIdentifier("identifier1");
        Survey survey2 = TestUtils.getSurvey(false);
        survey2.setIdentifier("identifier2");
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(any(), any())).thenReturn(survey1);
        when(mockSurveyService.getSurvey(any())).thenReturn(survey2);
        surveyGuid1 = survey1.getGuid();
        surveyGuid2 = survey2.getGuid();
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnCreate() {
        SchedulePlan plan = createSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.createSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).createSchedulePlan(any(), spCaptor.capture());
        
        List<Activity> activities = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities();
        assertEquals("identifier1", activities.get(0).getSurvey().getIdentifier());
        assertNotNull(activities.get(1).getTask());
        assertEquals("identifier2", activities.get(2).getSurvey().getIdentifier());
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnUpdate() {
        SchedulePlan plan = createSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        service.updateSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).updateSchedulePlan(any(), spCaptor.capture());
        
        List<Activity> activities = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities();
        assertEquals("identifier1", activities.get(0).getSurvey().getIdentifier());
        assertNotNull(activities.get(1).getTask());
        assertEquals("identifier2", activities.get(2).getSurvey().getIdentifier());
    }

    @Test
    public void doNotUseIdentifierFromClient() {
        
        
        // The survey GUID/createdOn identify a survey, but the identifier from the client can just be 
        // mismatched by the client, so ignore it and look it up from the DB using the primary keys.
        Activity activity = new Activity.Builder().withLabel("A survey activity")
                .withPublishedSurvey("junkIdentifier", surveyGuid1).build();
        SchedulePlan plan = createSchedulePlan();
        plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().set(0, activity);
        
        // Verify that this was set.
        String identifier = plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertEquals("junkIdentifier", identifier);
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        service.updateSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).updateSchedulePlan(any(), spCaptor.capture());
        
        // It was not used.
        identifier = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertNotEquals("junkIdentifier", identifier);
        
    }
    
    @Test
    public void verifyCreateDoesNotUseProvidedGUIDs() throws Exception {
        SchedulePlan plan = createSchedulePlan();
        plan.setVersion(2L);
        plan.setGuid("AAA");
        Set<String> existingActivityGUIDs = Sets.newHashSet();
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (Activity activity : schedule.getActivities()) {
                existingActivityGUIDs.add(activity.getGuid());
            }
        }
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        service.createSchedulePlan(study, plan);
        
        verify(mockSchedulePlanDao).createSchedulePlan(any(), spCaptor.capture());
        
        SchedulePlan updatedPlan = spCaptor.getValue();
        assertNotEquals("AAA", updatedPlan.getGuid());
        assertNotEquals(new Long(2L), updatedPlan.getVersion());
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (Activity activity : schedule.getActivities()) {
                assertFalse( existingActivityGUIDs.contains(activity.getGuid()) );
            }
        }
        
    }
    @Test
    public void schedulePlanSetsStudyIdentifierOnCreate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = getSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(mockSchedulePlanDao.createSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.createSchedulePlan(anotherStudy, plan);
        assertEquals("another-study", plan.getStudyKey());
    }
    
    @Test
    public void schedulePlanSetsStudyIdentifierOnUpdate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = getSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.updateSchedulePlan(anotherStudy, plan);
        assertEquals("another-study", plan.getStudyKey());
    }
    
    @Test
    public void validatesOnCreate() {
        // Check that 1) validation is called and 2) the study's enumerations are used in the validation
        SchedulePlan plan = createInvalidSchedulePlan();
        try {
            service.createSchedulePlan(study, plan);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: taskGuid, CCC, tapTest.", e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0));
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
            assertEquals("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: taskGuid, CCC, tapTest.", e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0));
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
    
    private SchedulePlan createSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        // No identifier, which is the key here. This is valid, but we fill it out during saves as a convenience 
        // for the client. No longer required in the API.
        // Create a schedule plan with 3 activities to verify all activities are processed.
        schedule.addActivity(new Activity.Builder().withLabel("Activity 1").withPublishedSurvey(null, surveyGuid1).build());
        schedule.addActivity(new Activity.Builder().withLabel("Activity 2").withTask("taskGuid").build());
        schedule.addActivity(new Activity.Builder().withLabel("Activity 3").withSurvey(null, surveyGuid2, DateTime.now()).build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("This is a label");
        plan.setStrategy(strategy);
        plan.setStudyKey("study-key");
        plan.setGuid("BBB");
        return plan;
    }
    
}
