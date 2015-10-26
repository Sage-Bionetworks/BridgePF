package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
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

import com.google.common.collect.Sets;

public class SchedulePlanServiceMockTest {

    private String surveyGuid1;
    private String surveyGuid2;
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
        
        service.createSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).createSchedulePlan(spCaptor.capture());
        
        List<Activity> activities = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities();
        assertEquals("identifier1", activities.get(0).getSurvey().getIdentifier());
        assertNotNull(activities.get(1).getTask());
        assertEquals("identifier2", activities.get(2).getSurvey().getIdentifier());
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnUpdate() {
        SchedulePlan plan = createSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.updateSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).updateSchedulePlan(spCaptor.capture());
        
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
        
        service.updateSchedulePlan(plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any());
        verify(mockSurveyService).getSurvey(any());
        verify(mockSchedulePlanDao).updateSchedulePlan(spCaptor.capture());
        
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
        service.createSchedulePlan(plan);
        
        verify(mockSchedulePlanDao).createSchedulePlan(spCaptor.capture());
        
        SchedulePlan updatedPlan = spCaptor.getValue();
        assertNotEquals("AAA", updatedPlan.getGuid());
        assertNotEquals(new Long(2L), updatedPlan.getVersion());
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (Activity activity : schedule.getActivities()) {
                assertFalse( existingActivityGUIDs.contains(activity.getGuid()) );
            }
        }
        
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
        return plan;
    }
    
}
