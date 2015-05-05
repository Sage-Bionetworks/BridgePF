package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseWithSurvey;

public interface SurveyResponseService {
    
    public SurveyResponseWithSurvey createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers);
    
    public SurveyResponseWithSurvey createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers, String identifier);
    
    public SurveyResponseWithSurvey getSurveyResponse(String healthCode, String identifier);
    
    public SurveyResponseWithSurvey appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponses(String healthCode);

}
