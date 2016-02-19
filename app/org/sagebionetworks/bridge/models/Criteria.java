package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoCriteria.class)
public interface Criteria extends BridgeEntity {
    
    public static Criteria create() {
        return new DynamoCriteria();
    }
    
    public static Criteria create(Integer minAppVersion, Integer maxAppVersion, Set<String> allOfGroups, Set<String> noneOfGroups) {
        DynamoCriteria crit = new DynamoCriteria();
        crit.setMinAppVersion(minAppVersion);
        crit.setMaxAppVersion(maxAppVersion);
        crit.setAllOfGroups(allOfGroups);
        crit.setNoneOfGroups(noneOfGroups);
        return crit;
    }
    
    public static Criteria copy(Criteria criteria) {
        DynamoCriteria crit = new DynamoCriteria();
        if (criteria != null) {
            crit.setKey(criteria.getKey());
            crit.setMinAppVersion(criteria.getMinAppVersion());
            crit.setMaxAppVersion(criteria.getMaxAppVersion());
            crit.setNoneOfGroups(criteria.getNoneOfGroups());
            crit.setAllOfGroups(criteria.getAllOfGroups());
        }
        return crit;
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
