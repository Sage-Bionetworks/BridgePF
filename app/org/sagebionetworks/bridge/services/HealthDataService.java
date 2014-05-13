package org.sagebionetworks.bridge.services;

import java.util.Date;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public interface HealthDataService {

    public String appendHealthData(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException;
    
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException;
    
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, Date startDate, Date endDate) throws BridgeServiceException;
    
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException;

    public void updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException;

    public void deleteHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException;
    
}
