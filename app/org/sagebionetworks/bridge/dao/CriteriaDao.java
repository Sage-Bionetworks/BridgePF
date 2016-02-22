package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.Criteria;

public interface CriteriaDao {

    /**
     * Create the criteria object, or update it if the supplied criteria object (as 
     * defined by its key) exists. Cannot throw an EntityNotFoundException.
     */
    Criteria createOrUpdateCriteria(Criteria criteria);
    
    /**
     * Get the criteria object, or return null if it does not exist (does not throw 
     * an EntityNotFoundException).
     */
    Criteria getCriteria(String key);

    /**
     * Delete the criteria if it exists (if criteria does not exist, does not throw
     * EntityNotFoundException).
     */
    void deleteCriteria(String key);

}
