package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.Criteria;

public interface CriteriaDao {

    /**
     * While moving this interface from model objects, to a separate table, we must 
     * copy the values over to a proper Criteria model object, either from an existing 
     * Criteria object, from Subpopulation, or from SchedulePlan. This can go away at
     * the end of migration. 
     */
    Criteria copyCriteria(String key, Criteria criteria);
    
    void createOrUpdateCriteria(Criteria criteria);
    
    Criteria getCriteria(String key);
    
    void deleteCriteria(String key);

}
