package org.sagebionetworks.bridge.services;

import java.util.Date;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.healthdata.HealthDataEntry;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;

public interface HealthDataService {

    public String appendHealthData(HealthDataKey key, HealthDataEntry entry) throws BridgeServiceException;
    
    public List<HealthDataEntry> getAllHealthData(HealthDataKey key) throws BridgeServiceException;
    
    public List<HealthDataEntry> getHealthDataByDateRange(HealthDataKey key, Date startDate, Date endDate) throws BridgeServiceException;
    
    public HealthDataEntry getHealthDataEntry(HealthDataKey key, String recordId) throws BridgeServiceException;

    public void updateHealthDataEntry(HealthDataKey key, HealthDataEntry entry) throws BridgeServiceException;

    public void deleteHealthDataEntry(HealthDataKey key, String recordId) throws BridgeServiceException;
    
}
