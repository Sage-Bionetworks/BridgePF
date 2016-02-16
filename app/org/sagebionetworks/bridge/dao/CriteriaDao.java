package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.Criteria;

public interface CriteriaDao {

    Criteria copyCriteria(String key, Criteria criteria);
    
    void createOrUpdateCriteria(Criteria criteria);
    
    Criteria getCriteria(String key);
    
    void deleteCriteria(String key);

}
