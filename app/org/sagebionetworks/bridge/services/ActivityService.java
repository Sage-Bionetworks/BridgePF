package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.models.schedules.SurveyReference.SURVEY_PATH_FRAGMENT;
import static org.sagebionetworks.bridge.models.schedules.SurveyReference.SURVEY_RESPONSE_PATH_FRAGMENT;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

/**
 * Services related to dealing with the mess that is an activity object. Refactoring to follow.
 */
@Component("activityService")
public class ActivityService {
    
    private static final List<SurveyAnswer> EMPTY_ANSWERS = ImmutableList.of();
    
    private SurveyService surveyService;
    
    private SurveyResponseService surveyResponseService;
    
    @Autowired
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    @Autowired
    public void setSurveyResponseService(SurveyResponseService surveyResponseService) {
        this.surveyResponseService = surveyResponseService;
    }

    public Activity createResponseActivityIfNeeded(StudyIdentifier studyIdentifier, String healthCode, Activity activity) {
        if ((activity.getActivityType() != ActivityType.SURVEY)) {
            return activity;
        }
        String baseUrl = activity.getRef().split(SURVEY_PATH_FRAGMENT)[0];
        SurveyResponse response = null;
        
        SurveyReference ref = activity.getSurvey();
        GuidCreatedOnVersionHolder keys = ref.getGuidCreatedOnVersionHolder();
        if (keys == null) {
            keys = surveyService.getSurveyMostRecentlyPublishedVersion(studyIdentifier, ref.getGuid());
        }   
        response = surveyResponseService.createSurveyResponse(keys, healthCode, EMPTY_ANSWERS);
        String url = baseUrl + SURVEY_RESPONSE_PATH_FRAGMENT + response.getGuid();
        return new Activity(activity.getLabel(), url);
    }
    
}
