package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;

public interface SurveyQuestion {
    
    public String getSurveyGuid();
    public void setSurveyGuid(String surveyGuid);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public int getOrder();
    public void setOrder(int order);
    
    // type, prompt, minValue, maxValue, options, etc.
    public JsonNode getData();
    public void setData(JsonNode data);
    
}
