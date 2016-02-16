package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.Criteria;

public interface CriteriaDao {

    public void createOrUpdateCriteria(Criteria criteria);
    
    public Criteria getCriteria(String key);
    
    public void deleteCriteria(String key);

}
