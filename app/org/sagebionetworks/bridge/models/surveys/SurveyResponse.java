package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("SurveyResponse")
public interface SurveyResponse extends BridgeEntity {
    
    public enum Status {
        UNSTARTED,
        IN_PROGRESS,
        FINISHED;
    }

    public Survey getSurvey();
    public void setSurvey(Survey survey);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getHealthCode();
    public void setHealthCode(String healthCode);
    
    public Status getStatus();
    
    public Long getStartedOn();
    public void setStartedOn(Long startedOn);
    
    public Long getCompletedOn();
    public void setCompletedOn(Long completedOn);
    
    public List<SurveyAnswer> getAnswers();
    public void setAnswers(List<SurveyAnswer> answers);
 
}
