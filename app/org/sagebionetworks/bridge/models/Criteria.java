package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoCriteria.class)
public interface Criteria extends BridgeEntity {
    
    String getKey();
    
    Integer getMinAppVersion();
    
    Integer getMaxAppVersion();
    
    Set<String> getAllOfGroups();
    
    Set<String> getNoneOfGroups();

}
