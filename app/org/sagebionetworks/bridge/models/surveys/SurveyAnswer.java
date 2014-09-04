package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Once retrieved we'll want to sort these by the order of the questions
 * in the survey.
 */
public interface SurveyAnswer {

    public String getSurveyResponseGuid();
    public void setSurveyResponseGuid(String surveyResponseGuid);
    
    public String getSurveyQuestionGuid();
    public void setSurveyQuestionGuid(String surveyQuestionGuid);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public JsonNode getData();
    public void setData(JsonNode data);
    
}
