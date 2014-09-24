package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.IdVersionHolder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

public class HealthDataServiceImpl implements HealthDataService {

    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;

    public void setCreateMapper(DynamoDBMapper createMapper) {
        this.createMapper = createMapper;
    }

    public void setUpdateMapper(DynamoDBMapper updateMapper) {
        this.updateMapper = updateMapper;
    }
    
    private List<HealthDataRecord> toHealthDataEntries(Collection<DynamoHealthDataRecord> records) {
        List<HealthDataRecord> entries = new ArrayList<HealthDataRecord>(records.size());
        for (DynamoHealthDataRecord r : records) {
            entries.add(r.toHealthDataRecord());
        }
        return entries;
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
        try {
            List<IdVersionHolder> ids = Lists.newArrayListWithCapacity(records.size());
            List<DynamoHealthDataRecord> recordsToSave = Lists.newArrayListWithCapacity(records.size());

            for (HealthDataRecord record : records) {
                if (record.getStartDate() == 0) {
                    throw new BridgeServiceException("New health data record instance does not have a startDate", HttpStatus.SC_BAD_REQUEST);
                }
                
                String recordId = BridgeUtils.generateGuid();
                record.setRecordId(recordId);
                recordsToSave.add( new DynamoHealthDataRecord(key.toString(), record) );
            }
            // TODO: The docs say that individual records in the batch can fail, and that these will be retried
            // with exponential back-off. However, I could find no examples to verify that all this happens, or 
            // is configured in a way that we would be happy with (how many retries, what's the back-off, etc.)
            createMapper.batchSave(recordsToSave);
            
            for (DynamoHealthDataRecord record : recordsToSave) {
                ids.add( new IdVersionHolder(record.getRecordId(), record.getVersion()) );
            }
            return ids;
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey key is null", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString());
            DynamoDBQueryExpression<DynamoHealthDataRecord> queryExpression = new DynamoDBQueryExpression<DynamoHealthDataRecord>()
                    .withHashKeyValues(dynamoRecord);

            List<DynamoHealthDataRecord> records = updateMapper.query(DynamoHealthDataRecord.class, queryExpression);
            return toHealthDataEntries(records);
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // To do this we would need a secondary index on the endDate.
    // (startDate <= windowEnd && (endDate == null || endDate >= windowStart))
    
    // For any local secondary index, you can store up to 10 GB of data per distinct hash key value. 
    // I think this is okay. That's per user per tracker per study. That's 160k records.
    
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
        try {
            /* Works for sure, very inefficient. Code below this at least queries out records that start after 
             * the query window. 
            return FluentIterable.from(getAllHealthData(key)).filter(new Predicate<HealthDataRecord>() {
                public boolean apply(HealthDataRecord record) {
                    if ((record.getEndDate() != 0 && record.getEndDate() < startDate.getTime())
                      || record.getStartDate() > endDate.getTime()) {
                        return false;
                    }
                    return true;
                }
            }).toList();
            */
            // Find records whose start date is before the window end date, and whose end date is after the window start date (or zero)
            DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString());

            Condition isLessThanOrEqualToEndDateWindow = new Condition()
                .withComparisonOperator(ComparisonOperator.LE.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(endDate)));

            DynamoDBQueryExpression<DynamoHealthDataRecord> queryExpression = new DynamoDBQueryExpression<DynamoHealthDataRecord>()
                .withHashKeyValues(dynamoRecord)
                .withRangeKeyCondition("startDate", isLessThanOrEqualToEndDateWindow);
            
            /* DynamoDB cannot make compound queries like this. I'm about 99% sure of it at this point.
             * We have to pull more records than we intend, and then filter.
            Condition isGreaterThanOrEqualToStartDateWindow = new Condition()
                .withComparisonOperator(ComparisonOperator.GE.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(startDate.getTime())));
            
            Condition isEqualToZero = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withN("0"));

            DynamoDBQueryExpression<DynamoRecord> queryExpression2 = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(dynamoRecord)
                .withRangeKeyCondition("endDate", isGreaterThanOrEqualToStartDateWindow)
                .withRangeKeyCondition("endDate", isEqualToZero);
            */

            List<DynamoHealthDataRecord> records = updateMapper.query(DynamoHealthDataRecord.class, queryExpression);
            
            return toHealthDataEntries(FluentIterable.from(records).filter(new Predicate<DynamoHealthDataRecord>() {
                public boolean apply(DynamoHealthDataRecord record) {
                    return !(record.getEndDate() != 0 && record.getEndDate() < startDate);
                }
            }).toList());
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (recordId == null) {
            throw new BridgeServiceException("HealthDataRecord record ID cannot be null", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString());
            dynamoRecord.setRecordId(recordId);
            dynamoRecord = updateMapper.load(dynamoRecord);
            if (dynamoRecord == null) {
                throw new BridgeServiceException("Health data record cannot be found", HttpStatus.SC_NOT_FOUND);
            }
            return dynamoRecord.toHealthDataRecord();
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
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
        try {
            DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString(), record);
            updateMapper.save(dynamoRecord);
            return new IdVersionHolder(dynamoRecord.getRecordId(), dynamoRecord.getVersion());

        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
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
        try {
            HealthDataRecord record = getHealthDataRecord(key, recordId);
            if (record == null) {
                throw new BridgeServiceException("Object does not exist: record ID #" + recordId,
                        HttpStatus.SC_NOT_FOUND);
            }
            DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString(), recordId, record);
            updateMapper.delete(dynamoRecord);
            
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
