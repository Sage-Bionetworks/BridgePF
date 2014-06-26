package org.sagebionetworks.bridge.dao;

public interface HealthCodeDao {
    boolean setIfNotExist(String code);
}
