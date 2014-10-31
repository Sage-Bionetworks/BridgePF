package org.sagebionetworks.bridge.dao;

public interface HealthCodeDao {
    
    /**
     * @return true, if the code does not exist yet and is set; false, if the code already exists.
     */
    boolean setIfNotExist(String code);
    
    void deleteCode(String healthCode);
}
