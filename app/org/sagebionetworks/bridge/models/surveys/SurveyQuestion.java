package org.sagebionetworks.bridge.models.surveys;

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
    
    public String getPrompt();
    public void setPrompt(String prompt);
    
    public UIHint getUiHint();
    public void setUiHint(UIHint hint);

    public Constraints getConstraints();
    public void setConstraints(Constraints constraints);
}
