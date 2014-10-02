package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
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
            throw new BadRequestException("Cannot create a new HealthDataRecord instance without specifying a HealthDataKey");
        } else if (records == null) {
            throw new BadRequestException("Health data records are null");
        } else if (records.isEmpty()) {
            throw new BadRequestException("No health data records to add");
        }
        records = healthDataDao.appendHealthData(key, records);
        return Lists.transform(records, TRANSFORMER);
    }
    
    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        if (key == null) {
            throw new BadRequestException("HealthDataKey key is null");
        }
        return healthDataDao.getAllHealthData(key);
    }
    
    @Override
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, final long startDate, final long endDate) throws BridgeServiceException {
        if (key == null) {
            throw new BadRequestException("HealthDataKey cannot be null");
        } else if (startDate <= 0) {
            throw new BadRequestException("startDate cannot be null/0");
        } else if (endDate <= 0) {
            throw new BadRequestException("endDate cannot be null/0");
        } else if (endDate < startDate) {
            throw new BadRequestException("endDate cannot be less than startDate.");
        }
        return healthDataDao.getHealthDataByDateRange(key, startDate, endDate);
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (recordId == null) {
            throw new BadRequestException("HealthDataRecord record ID cannot be null");
        }
        return healthDataDao.getHealthDataRecord(key, recordId);
    }

    @Override
    public IdVersionHolder updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException {
        if (key == null) {
            throw new BadRequestException("HealthDataKey is required on update (it's null)");
        } else if (record == null) {
            throw new BadRequestException("HealthDataRecord is required on update (it's null)");
        } else if (record.getVersion() == null) {
            throw new BadRequestException("HealthDataRecord requires version on update");
        } else if (record.getStartDate() == 0L) {
            throw new BadRequestException(
                    "HealthDataRecord startDate & endDate are required on update (point-in-time events set the same time for both fields");
        } else if (record.getRecordId() == null) {
            throw new BadRequestException("HealthDataRecord record ID is required on update (it's null)");
        }
        record = healthDataDao.updateHealthDataRecord(key, record);
        return new IdVersionHolder(record.getRecordId(), record.getVersion());
    }
    
    @Override
    public void deleteHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (key == null) {
            throw new BadRequestException("HealthDataKey is required for delete (it's null)");
        } else if (recordId == null) {
            throw new BadRequestException("HealthDataRecord record ID is required for delete (it's null)");
        }
        healthDataDao.deleteHealthDataRecord(key, recordId);
    }

}
