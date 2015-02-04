package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurvey.class)
@BridgeTypeName("Survey")
public interface Survey extends GuidCreatedOnVersionHolder, BridgeEntity  {

    public String getStudyIdentifier();
    public void setStudyIdentifier(String studyIdentifier);
    
    public String getGuid();
    public void setGuid(String guid);
    
    public long getCreatedOn();
    public void setCreatedOn(long createdOn);
    
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
    
    public List<SurveyElement> getElements();
    public void setElements(List<SurveyElement> elements);
    
    public List<SurveyQuestion> getUnmodifiableQuestionList();
    
}
