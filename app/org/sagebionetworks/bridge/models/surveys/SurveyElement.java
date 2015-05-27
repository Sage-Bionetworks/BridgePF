package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.json.JsonNodeToSurveyElementConverter;

@JsonDeserialize(converter = JsonNodeToSurveyElementConverter.class)
public interface SurveyElement {

    public String getSurveyCompoundKey();
    public void setSurveyCompoundKey(String surveyCompoundKey);

    public void setSurveyKeyComponents(String surveyGuid, long createdOn);

    public String getGuid();
    public void setGuid(String guid);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public int getOrder();
    public void setOrder(int order);
    
    public String getType();
    public void setType(String type);
    
    public JsonNode getData();
    public void setData(JsonNode data);
    
}

