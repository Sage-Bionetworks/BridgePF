package org.sagebionetworks.bridge.dao;

public interface HealthIdDao {

    boolean setIfNotExist(String id, String code);

    String getCode(String id);
}
