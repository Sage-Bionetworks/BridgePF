package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;

public class SchedulePlanServiceMockTest {

    private String surveyGuid;
    private SchedulePlanServiceImpl service;
    
    private SchedulePlanDao mockSchedulePlanDao;
    private SurveyService mockSurveyService;
    
    @Before
    public void before() {
        mockSchedulePlanDao = mock(SchedulePlanDao.class);
        mockSurveyService = mock(SurveyService.class);
        
        service = new SchedulePlanServiceImpl();
        service.setSchedulePlanDao(mockSchedulePlanDao);
        service.setSurveyService(mockSurveyService);
        service.setValidator(new SchedulePlanValidator());
        
        Survey survey = TestUtils.getSurvey(false);
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(any(), any())).thenReturn(survey);
        surveyGuid = survey.getGuid();
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnCreate() {
        SchedulePlan plan = createSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.createSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSchedulePlanDao).createSchedulePlan(spCaptor.capture());
        
        String identifier = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertNotNull(identifier);
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnUpdate() {
        SchedulePlan plan = createSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.updateSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSchedulePlanDao).updateSchedulePlan(spCaptor.capture());
        
        String identifier = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertNotNull(identifier);
    }

    @Test
    public void doNotUseIdentifierFromClient() {
        // The survey GUID/createdOn identify a survey, but the identifier from the client can just be 
        // mismatched by the client, so ignore it and look it up from the DB using the primary keys.
        Activity activity = new Activity.Builder().withLabel("A survey activity")
                .withPublishedSurvey("junkIdentifier", surveyGuid).build();
        SchedulePlan plan = createSchedulePlan();
        plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().set(0, activity);
        
        // Verify that this was set.
        String identifier = plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertEquals("junkIdentifier", identifier);
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.updateSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSchedulePlanDao).updateSchedulePlan(spCaptor.capture());
        
        // It was not used.
        identifier = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertNotEquals("junkIdentifier", identifier);
        
    }
    
    private SchedulePlan createSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        // No identifier, which is the key here. This is valid, but we fill it out during saves as a convenience 
        // for the client. No longer required in the API.
        schedule.addActivity(new Activity.Builder().withLabel("A survey activity").withPublishedSurvey(surveyGuid).build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("This is a label");
        plan.setStrategy(strategy);
        plan.setStudyKey("study-key");
        return plan;
    }
    
}
