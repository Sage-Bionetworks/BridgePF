package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface SurveyQuestion {
    
    public String getSurveyCompoundKey();
    public void setSurveyCompoundKey(String surveyCompoundKey);
    
    public void setSurveyKeyComponents(String surveyGuid, long versionedOn);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public int getOrder();
    public void setOrder(int order);
    
    // When we're done, will we even need this? I don't think so... only in the dynamo implementation.
    public ObjectNode getData();
    public void setData(ObjectNode data);

    // prompt, declined, uihint
    public String getPrompt();
    public boolean getDeclined();
    public List<String> getUiHints();

    // minValue, maxValue, enumerated options, etc. These would
    // vary by the data type.
    public Constraints getConstraints();

    public String getType(); // Not the name of the class, e.g. "SurveyQuestion". The data type of the question.
}
