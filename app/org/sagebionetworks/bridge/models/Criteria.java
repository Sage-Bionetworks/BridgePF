package org.sagebionetworks.bridge.models;

import java.util.Set;

public interface Criteria extends BridgeEntity {
    
    public Integer getMinAppVersion();
    
    public Integer getMaxAppVersion();
    
    public Set<String> getAllOfGroups();
    
    public Set<String> getNoneOfGroups();

}
