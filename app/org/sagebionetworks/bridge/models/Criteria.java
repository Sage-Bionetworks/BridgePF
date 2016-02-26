package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoCriteria.class)
public interface Criteria extends BridgeEntity {
    
    public static Criteria create() {
        return new DynamoCriteria();
    }

    /** 
     * The foreign key to the object filtered with these criteria. It's the model and the model's
     * keys, e.g. "subpopulation:<guid>".
     */
    String getKey();
    void setKey(String key);
    
    String getLanguage();
    void setLanguage(String language);
    
    /**
     * The object associated with these criteria should be matched only if the application version 
     * supplied by the client is equal to or greater than the minAppVersion. If null, there is no 
     * minimum required version.
     */
    Integer getMinAppVersion();
    void setMinAppVersion(Integer minAppVersion);
    
    /**
     * The object associated with these criteria should be matched only if the application version 
     * supplied by the client is less that or equal to the maxAppVersion. If null, there is no 
     * maximum required version.
     */
    Integer getMaxAppVersion();
    void setMaxAppVersion(Integer maxAppVersion);
    
    /**
     * The object associated with these criteria should be matched only if the user has all of the 
     * groups contained in this set of data groups. If the set is empty, there are no required 
     * data groups. 
     */
    Set<String> getAllOfGroups();
    void setAllOfGroups(Set<String> allOfGroups);
    
    /**
     * The object associated with these criteria should be matched only if the user has none of the 
     * groups contained in this set of data groups. If the set is empty, there are no prohibited 
     * data groups. 
     */
    Set<String> getNoneOfGroups();
    void setNoneOfGroups(Set<String> noneOfGroups);
}
