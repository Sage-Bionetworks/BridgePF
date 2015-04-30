package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public interface SurveyResponseService {
    
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers);
    
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers, String identifier);
    
    public SurveyResponse getSurveyResponse(String healthCode, String guid);
    
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponses(String healthCode);

}
