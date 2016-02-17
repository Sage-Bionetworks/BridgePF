package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoCriteria.class)
public interface Criteria extends BridgeEntity {
    
    public static Criteria create() {
        return new DynamoCriteria();
    }
    
    void setKey(String key);
    String getKey();
    
    void setMinAppVersion(Integer minAppVersion);
    Integer getMinAppVersion();
    
    void setMaxAppVersion(Integer maxAppVersion);
    Integer getMaxAppVersion();
    
    void setAllOfGroups(Set<String> allOfGroups);
    Set<String> getAllOfGroups();
    
    void setNoneOfGroups(Set<String> noneOfGroups);
    Set<String> getNoneOfGroups();
}
