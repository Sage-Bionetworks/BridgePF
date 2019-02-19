package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoCriteria.class)
public interface Criteria extends BridgeEntity {
    
    static Criteria create() {
        return new DynamoCriteria();
    }

    /** 
     * The foreign key to the object filtered with these criteria. It's the model type and the model's
     * keys, e.g. "subpopulation:<guid>".
     */
    String getKey();
    void setKey(String key);
    
    /**
     * The object associated with these criteria should only be matched if the user has this language 
     * in their list of desired languages. This should be a two-letter language code, e.g. fr, de, 
     * es, or en.
     */
    String getLanguage();
    void setLanguage(String language);
    
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
    
    /**
     * The object associated with these criteria should be matched only if the user is associated to 
     * all of the substudies in this set of substudy IDs. If the set is empty, there are no required 
     * substudies. 
     */
    Set<String> getAllOfSubstudyIds();
    void setAllOfSubstudyIds(Set<String> substudyIds);
    
    /**
     * The object associated with these criteria should be matched only if the user is associated to 
     * none of the substudies contained in this set of substudy IDs. If the set is empty, there are 
     * no prohibited substudies. 
     */
    Set<String> getNoneOfSubstudyIds();
    void setNoneOfSubstudyIds(Set<String> substudyIds);
    
    /**
     * Minimum required app version for this criteria to match, specified for an operating system. If 
     * the operating system name is specified in the User-Agent string, then the app version must be 
     * equal to or greater than this value.
     */
    Integer getMinAppVersion(String osName);
    void setMinAppVersion(String osName, Integer minAppVersion);
    
    /**
     * Maximum required app version for this criteria to match, specified for an operating system. If 
     * the operating system name is specified in the User-Agent string, then the app version must be 
     * equal to or less than this value.
     */
    Integer getMaxAppVersion(String osName);
    void setMaxAppVersion(String osName, Integer maxAppVersion);
    
    /**
     * Get all the operating system names that are used to declare either minimum or maximum app 
     * versions (or both). Used to iterate through these collections.
     */
    Set<String> getAppVersionOperatingSystems();
}
