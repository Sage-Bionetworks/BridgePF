package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

import org.sagebionetworks.bridge.json.JsonNodeToSurveyElementConverter;

@JsonDeserialize(converter = JsonNodeToSurveyElementConverter.class)
public interface SurveyElement {

    public static final String CONSTRAINTS_PROPERTY = "constraints";
    public static final String FIRE_EVENT_PROPERTY = "fireEvent";
    public static final String GUID_PROPERTY = "guid";
    public static final String IDENTIFIER_PROPERTY = "identifier";
    public static final String IMAGE_PROPERTY = "image";
    public static final String PROMPT_DETAIL_PROPERTY = "promptDetail";
    public static final String PROMPT_PROPERTY = "prompt";
    public static final String BEFORE_RULES_PROPERTY = "beforeRules";
    public static final String AFTER_RULES_PROPERTY = "afterRules";
    public static final String TITLE_PROPERTY = "title";
    public static final String TYPE_PROPERTY = "type";    
    public static final String UI_HINTS_PROPERTY = "uiHint";
    
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
    
    List<SurveyRule> getBeforeRules();
    void setBeforeRules(List<SurveyRule> beforeRules);
    
    List<SurveyRule> getAfterRules();
    void setAfterRules(List<SurveyRule> afterRules);
    
}

