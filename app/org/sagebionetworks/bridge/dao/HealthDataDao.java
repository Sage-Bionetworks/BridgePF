package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public interface HealthDataDao {

    public List<HealthDataRecord> appendHealthData(HealthDataKey key, List<HealthDataRecord> records);
    
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key);
    
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, long startDate, long endDate);
    
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId);
    
    public HealthDataRecord updateHealthDataRecord(HealthDataKey key, HealthDataRecord record);
    
    public void deleteHealthDataRecord(HealthDataKey key, String recordId);
    
}
