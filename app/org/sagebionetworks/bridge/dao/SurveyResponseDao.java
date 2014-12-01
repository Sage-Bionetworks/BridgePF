package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public interface SurveyResponseDao {

    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyCreatedOn, String healthCode,
            List<SurveyAnswer> answers);
    
    public SurveyResponse createSurveyResponseWithGuid(String surveyGuid, long surveyCreatedOn, String healthCode,
            List<SurveyAnswer> answers, String responseGuid);
    
    public SurveyResponse getSurveyResponse(String guid);
    
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponse(SurveyResponse response);
    
}
