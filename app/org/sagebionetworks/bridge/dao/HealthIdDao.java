package org.sagebionetworks.bridge.dao;

/**
 * Manages one-way mapping from health ID to health code.
 */
public interface HealthIdDao {

    /**
     * @return true, if the id does not exist and is set; false if the id already exists.
     */
    boolean setIfNotExist(String id, String code);

    /**
     * Given a health ID, gets the health code.
     */
    String getCode(String id);
    
}
