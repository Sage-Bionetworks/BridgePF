package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public interface SurveyResponseService {
    
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyCreatedOn, String healthCode,
            List<SurveyAnswer> answers);
    
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyCreatedOn, String healthCode,
            List<SurveyAnswer> answers, String identifier);
    
    public SurveyResponse getSurveyResponse(String healthCode, String identifier);
    
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponse(SurveyResponse response);

}
