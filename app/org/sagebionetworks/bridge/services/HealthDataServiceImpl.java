package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.IdVersionHolder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class HealthDataServiceImpl implements HealthDataService {
    
    private static final Function<HealthDataRecord, IdVersionHolder> TRANSFORMER = new Function<HealthDataRecord, IdVersionHolder>() {
        @Override
        public IdVersionHolder apply(HealthDataRecord record) {
            return new IdVersionHolder(record.getRecordId(), record.getVersion());
        }
    };

    private HealthDataDao healthDataDao;
    
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }
    
    @Override
    public List<IdVersionHolder> appendHealthData(HealthDataKey key, List<HealthDataRecord> records) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("Cannot create a new HealthDataRecord instance without specifying a HealthDataKey", HttpStatus.SC_BAD_REQUEST);
        } else if (records == null) {
            throw new BridgeServiceException("Health data records are null", HttpStatus.SC_BAD_REQUEST);
        } else if (records.isEmpty()) {
            throw new BridgeServiceException("No health data records to add", HttpStatus.SC_BAD_REQUEST);
        }
        records = healthDataDao.appendHealthData(key, records);
        return Lists.transform(records, TRANSFORMER);
    }
    
    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey key is null", HttpStatus.SC_BAD_REQUEST);
        }
        return healthDataDao.getAllHealthData(key);
    }
    
    @Override
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, final long startDate, final long endDate) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null", HttpStatus.SC_BAD_REQUEST);
        } else if (startDate <= 0) {
            throw new BridgeServiceException("startDate cannot be null/0", HttpStatus.SC_BAD_REQUEST);
        } else if (endDate <= 0) {
            throw new BridgeServiceException("endDate cannot be null/0", HttpStatus.SC_BAD_REQUEST);
        } else if (endDate < startDate) {
            throw new BridgeServiceException("endDate cannot be less than startDate.", HttpStatus.SC_BAD_REQUEST);
        }
        return healthDataDao.getHealthDataByDateRange(key, startDate, endDate);
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (recordId == null) {
            throw new BridgeServiceException("HealthDataRecord record ID cannot be null", HttpStatus.SC_BAD_REQUEST);
        }
        return healthDataDao.getHealthDataRecord(key, recordId);
    }

    @Override
    public IdVersionHolder updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record == null) {
            throw new BridgeServiceException("HealthDataRecord is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record.getVersion() == null) {
            throw new BridgeServiceException("HealthDataRecord requires version on update", HttpStatus.SC_BAD_REQUEST);
        } else if (record.getStartDate() == 0L) {
            throw new BridgeServiceException(
                    "HealthDataRecord startDate & endDate are required on update (point-in-time events set the same time for both fields",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record.getRecordId() == null) {
            throw new BridgeServiceException("HealthDataRecord record ID is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        }
        record = healthDataDao.updateHealthDataRecord(key, record);
        return new IdVersionHolder(record.getRecordId(), record.getVersion());
    }
    
    @Override
    public void deleteHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required for delete (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (recordId == null) {
            throw new BridgeServiceException("HealthDataRecord record ID is required for delete (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        }
        healthDataDao.deleteHealthDataRecord(key, recordId);
    }

}
