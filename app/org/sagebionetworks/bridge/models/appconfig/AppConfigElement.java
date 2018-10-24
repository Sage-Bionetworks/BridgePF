package org.sagebionetworks.bridge.models.appconfig;

import org.sagebionetworks.bridge.dynamodb.DynamoAppConfigElement;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoAppConfigElement.class)
public interface AppConfigElement extends BridgeEntity {

    static AppConfigElement create() {
        return new DynamoAppConfigElement();
    }
    
    void setKey(String key);
    String getKey();
    
    
    void setStudyId(String studyId);
    String getStudyId();
    
    void setId(String id);
    String getId();
    
    void setRevision(Long revision);
    Long getRevision();
    
    void setDeleted(boolean deleted);
    boolean isDeleted();
    
    JsonNode getData();
    void setData(JsonNode data);
    
    void setCreatedOn(long createdOn);
    long getCreatedOn();
    
    void setModifiedOn(long modifiedOn);
    long getModifiedOn();
    
    void setVersion(Long version);
    Long getVersion();
}
