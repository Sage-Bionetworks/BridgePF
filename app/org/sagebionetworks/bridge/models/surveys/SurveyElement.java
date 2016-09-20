package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.json.JsonNodeToSurveyElementConverter;

@JsonDeserialize(converter = JsonNodeToSurveyElementConverter.class)
public interface SurveyElement {

    String getSurveyCompoundKey();
    void setSurveyCompoundKey(String surveyCompoundKey);

    void setSurveyKeyComponents(String surveyGuid, long createdOn);

    String getGuid();
    void setGuid(String guid);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    int getOrder();
    void setOrder(int order);
    
    String getType();
    void setType(String type);
    
    JsonNode getData();
    void setData(JsonNode data);
    
}

