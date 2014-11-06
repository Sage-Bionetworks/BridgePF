package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

public class DynamoHealthDataDao implements HealthDataDao {

    private class DynamoTransformer implements Function<HealthDataRecord,DynamoHealthDataRecord> {
        private String key;
        public DynamoTransformer(String key) {
            this.key = key;
        }
        @Override
        public DynamoHealthDataRecord apply(HealthDataRecord record) {
            return new DynamoHealthDataRecord(key, record);
        }
    }
    
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoHealthDataRecord.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    
    @Override
    public List<HealthDataRecord> appendHealthData(HealthDataKey key, List<HealthDataRecord> records) {
        
        DynamoTransformer transformer = new DynamoTransformer(key.toString());
        List<DynamoHealthDataRecord> dynamoRecords = Lists.transform(records, transformer);
        
        List<FailedBatch> failures = mapper.batchSave(dynamoRecords);
        BridgeUtils.ifFailuresThrowException(failures);
        return records;
    }

    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) {
        DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString());
        DynamoDBQueryExpression<DynamoHealthDataRecord> queryExpression = new DynamoDBQueryExpression<DynamoHealthDataRecord>()
                .withHashKeyValues(dynamoRecord);

        List<DynamoHealthDataRecord> records = mapper.query(DynamoHealthDataRecord.class, queryExpression);
        return new ArrayList<HealthDataRecord>(records);
    }

    // To do this we would need a secondary index on the endDate.
    // (startDate <= windowEnd && (endDate == null || endDate >= windowStart))
    
    // For any local secondary index, you can store up to 10 GB of data per distinct hash key value. 
    // I think this is okay. That's per user per tracker per study. That's 160k records.

    @Override
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, final long startDate, final long endDate) {
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

        List<DynamoHealthDataRecord> records = mapper.query(DynamoHealthDataRecord.class, queryExpression);
        
        return new ArrayList<HealthDataRecord>((FluentIterable.from(records).filter(new Predicate<DynamoHealthDataRecord>() {
            public boolean apply(DynamoHealthDataRecord record) {
                return !(record.getEndDate() != 0 && record.getEndDate() < startDate);
            }
        }).toList()));    
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String guid) {
        DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString());
        dynamoRecord.setGuid(guid);
        dynamoRecord = mapper.load(dynamoRecord);
        if (dynamoRecord == null) {
            throw new EntityNotFoundException(HealthDataRecord.class);
        }
        return dynamoRecord;
    }

    @Override
    public HealthDataRecord updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) {
        DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString(), record);
        mapper.save(dynamoRecord);
        return dynamoRecord;
    }

    @Override
    public void deleteHealthDataRecord(HealthDataKey key, String guid) {
        HealthDataRecord record = getHealthDataRecord(key, guid);
        DynamoHealthDataRecord dynamoRecord = new DynamoHealthDataRecord(key.toString(), guid, record);
        mapper.delete(dynamoRecord);
    }

}
