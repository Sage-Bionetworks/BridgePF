package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("Survey")
public interface Survey extends BridgeEntity {

    public String getStudyKey();
    public void setStudyKey(String studyKey);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public long getVersionedOn();
    public void setVersionedOn(long versionedOn);
    
    public long getModifiedOn();
    public void setModifiedOn(long modifiedOn);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getName();
    public void setName(String name);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);

    public boolean isPublished();
    public void setPublished(boolean published);
    
    public List<SurveyQuestion> getQuestions();
    public void setQuestions(List<SurveyQuestion> questions);
}
