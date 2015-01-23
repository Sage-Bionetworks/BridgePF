package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

public class HealthDataServiceImpl implements HealthDataService {
    
    private HealthDataDao healthDataDao;
    
    private Validator validator;
    
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }
    public void setValidator(Validator validator) {
        this.validator = validator;
    }
    
    @Override
    public List<HealthDataRecord> appendHealthData(HealthDataKey key, List<HealthDataRecord> records) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey key cannot be null");
        checkNotNull(records, "Health data records cannot be null");
        checkArgument(!records.isEmpty(), "No health data records to add");
        
        for (HealthDataRecord record : records) {
            record.setGuid(BridgeUtils.generateGuid());
            Validate.entityThrowingException(validator, record);
        }

        return healthDataDao.appendHealthData(key, records);
    }
    
    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey key cannot be null");

        return healthDataDao.getAllHealthData(key);
    }
    
    @Override
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, final long startDate, final long endDate) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey key cannot be null");

        // These are user entered query values
        if (startDate <= 0) {
            throw new BadRequestException("startDate cannot be <= 0");
        } else if (endDate <= 0) {
            throw new BadRequestException("endDate cannot be <= 0");
        } else if (endDate < startDate) {
            throw new BadRequestException("endDate cannot be less than startDate.");
        }
        return healthDataDao.getHealthDataByDateRange(key, startDate, endDate);
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String guid) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey cannot be null");
        checkNotNull(guid, "HealthDataRecord guid cannot be null");

        return healthDataDao.getHealthDataRecord(key, guid);
    }

    @Override
    public GuidVersionHolder updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey cannot be null");
        checkNotNull(record, "HealthDataRecord cannot be null");
        
        Validate.entityThrowingException(validator, record);
        
        record = healthDataDao.updateHealthDataRecord(key, record);
        return new GuidVersionHolder(record.getGuid(), record.getVersion());
    }
    
    @Override
    public void deleteHealthDataRecord(HealthDataKey key, String guid) throws BridgeServiceException {
        checkNotNull(key, "HealthDataKey cannot be null");
        checkNotNull(guid, "Health data guid cannot be null");

        healthDataDao.deleteHealthDataRecord(key, guid);
    }
    
    @Override
    public void deleteHealthDataRecords(HealthDataKey key) {
        checkNotNull(key, "HealthDataKey cannot be null");
        
        healthDataDao.deleteHealthDataRecords(key);
    }

}
