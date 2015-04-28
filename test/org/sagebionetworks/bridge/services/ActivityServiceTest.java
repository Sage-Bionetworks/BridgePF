package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public class ActivityServiceTest {

    private static final DateTime CREATED_ON = DateTime.parse("2014-10-02T08:40:32.341-07:00");
    
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("foo");
    
    private static final String HEALTH_CODE = "BBB";
    
    private ActivityService service;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        service = new ActivityService();
        
        SurveyResponse response = new DynamoSurveyResponse(HEALTH_CODE, "CCC");
        SurveyResponseService surveyResponseService = mock(SurveyResponseService.class);
        when(surveyResponseService.createSurveyResponse(any(GuidCreatedOnVersionHolder.class), anyString(), any(List.class))).thenReturn(response);
        
        Survey survey = new DynamoSurvey();
        survey.setGuid("AAA");
        survey.setCreatedOn(CREATED_ON.getMillis());
        
        SurveyService surveyService = mock(SurveyService.class);
        when(surveyService.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(survey);
        
        service.setSurveyResponseService(surveyResponseService);
        service.setSurveyService(surveyService);
    }
    
    @Test
    public void doesNotChangeNonSurveyActivity() {
        Activity activity = new Activity("Label", "task:foo");
        
        assertEquals(activity.getActivityType(), ActivityType.TASK);
        Activity after = service.createResponseActivityIfNeeded(STUDY_IDENTIFIER, HEALTH_CODE, activity);
        
        assertEquals(activity, after);
    }
    
    @Test
    public void changeAbsoluteSurveyActivity() {
        Activity activity = new Activity("Label", "survey:https://webservices.sagebridge.org/api/v1/surveys/AAA/"+CREATED_ON.toString());
        
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        Activity after = service.createResponseActivityIfNeeded(STUDY_IDENTIFIER, HEALTH_CODE, activity);
        
        assertEquals("survey:https://webservices.sagebridge.org/api/v1/surveys/response/BBB:CCC", after.getRef());
    }
    
    @Test
    public void changePublishedSurveyActivity() {
        Activity activity = new Activity("Label", "survey:https://webservices.sagebridge.org/api/v1/surveys/AAA/published");
        
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        Activity after = service.createResponseActivityIfNeeded(STUDY_IDENTIFIER, HEALTH_CODE, activity);
        
        assertEquals("survey:https://webservices.sagebridge.org/api/v1/surveys/response/BBB:CCC", after.getRef());
    }
    
    @Test
    public void anOddActivityIsLeftAlone() {
        Activity activity = new Activity("Label", "some random stuff");
        
        assertEquals(activity.getActivityType(), ActivityType.TASK);
        Activity after = service.createResponseActivityIfNeeded(STUDY_IDENTIFIER, HEALTH_CODE, activity);
        
        assertEquals(activity, after);
    }
}
