package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.Criteria;

public interface CriteriaDao {

    /**
     * This method is used to migrate the Criteria interface from Subpopulation and SchedulePlan to a separate Criteria
     * implementation. It copies the filtering information to a criteria object. This can go away at the end of
     * migration.
     */
    Criteria copyCriteria(String key, Criteria criteria);
    
    void createOrUpdateCriteria(Criteria criteria);
    
    Criteria getCriteria(String key);
    
    void deleteCriteria(String key);

}
