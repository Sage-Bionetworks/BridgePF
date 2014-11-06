package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public interface HealthDataService {

    public List<HealthDataRecord> appendHealthData(HealthDataKey key, List<HealthDataRecord> records);
    
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key);
    
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, long startDate, long endDate);
    
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String guid);

    public GuidVersionHolder updateHealthDataRecord(HealthDataKey key, HealthDataRecord record);

    public void deleteHealthDataRecord(HealthDataKey key, String guid);
    
}
