package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;

public interface SurveyResponseService {
    
    public SurveyResponseView createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers, String identifier);
    
    public SurveyResponseView getSurveyResponse(String healthCode, String identifier);
    
    public SurveyResponseView appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponses(String healthCode);

}
