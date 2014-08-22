package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

public interface SurveyResponse {
    
    public enum Status {
        UNSTARTED,
        IN_PROGRESS,
        FINISHED;
    }
    
    public String getUserId();
    public void setUserId(String userId);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public Survey getSurvey();
    public void setSurvey(Survey survey);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getHealthCode();
    public void setHealthCode(String healthCode);
    
    public Status getStatus();
    
    public long getStartedOn();
    public void setStartedOn(long startedOn);
    
    public long getCompletedOn();
    public void setCompletedOn(long completedOn);
    
    public List<SurveyAnswer> getAnswers();
    public void setAnswers(List<SurveyAnswer> answers);
 
}
