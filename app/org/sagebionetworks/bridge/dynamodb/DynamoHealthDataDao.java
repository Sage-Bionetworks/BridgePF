package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.dao.HealthDataDao}. */
@Component
public class DynamoHealthDataDao implements HealthDataDao {
    private static final long CREATED_ON_OFFSET_MILLIS = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);

    private DynamoDBMapper mapper;
    private DynamoIndexHelper healthCodeIndex;
    private DynamoIndexHelper uploadDateIndex;

    /** DynamoDB mapper for the HealthDataRecord table. This is configured by Spring. */
    @Resource(name = "healthDataDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * DynamoDB Index reference for the healthCode index. This is needed because the DynamoDB mapper does allow queries
     * using global secondary indices. This is configured by Spring
     */
    @Resource(name = "healthDataHealthCodeIndex")
    public void setHealthCodeIndex(DynamoIndexHelper healthCodeIndex) {
        this.healthCodeIndex = healthCodeIndex;
    }

    /**
     * DynamoDB Index reference for the uploadDate index. This is needed because the DynamoDB mapper does allow queries
     * using global secondary indices. This is configured by Spring
     */
    @Resource(name = "healthDataUploadDateIndex")
    public void setUploadDateIndex(DynamoIndexHelper uploadDateIndex) {
        this.uploadDateIndex = uploadDateIndex;
    }

    /** {@inheritDoc} */
    @Override
    public String createOrUpdateRecord(@Nonnull HealthDataRecord record) {
        DynamoHealthDataRecord dynamoRecord = (DynamoHealthDataRecord) record;

        if (StringUtils.isBlank(dynamoRecord.getId())) {
            // This record doesn't have its ID assigned yet (new record). Create an ID and assign it.
            String id = BridgeUtils.generateGuid();
            dynamoRecord.setId(id);
        }

        // persist to DDB
        mapper.save(dynamoRecord);
        return dynamoRecord.getId();
    }

    /** {@inheritDoc} */
    @Override
    public int deleteRecordsForHealthCode(@Nonnull String healthCode) {
        // query for the keys we need to delete
        List<HealthDataRecord> keysToDelete = healthCodeIndex.queryKeys(HealthDataRecord.class, "healthCode",
                healthCode, null);

        // and then delete
        List<DynamoDBMapper.FailedBatch> failureList = mapper.batchDelete(keysToDelete);
        BridgeUtils.ifFailuresThrowException(failureList);

        return keysToDelete.size();
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataRecord getRecordById(@Nonnull String id) {
        return mapper.load(DynamoHealthDataRecord.class, id);
    }

    /** {@inheritDoc} */
    @Override
    public List<HealthDataRecord> getRecordsForUploadDate(@Nonnull String uploadDate) {
        return uploadDateIndex.query(HealthDataRecord.class, "uploadDate", uploadDate, null);
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataRecordBuilder getRecordBuilder() {
        return new DynamoHealthDataRecord.Builder();
    }

    /** {@inheritDoc} */
    @Override
    public List<HealthDataRecord> getRecordsByHealthCodeCreatedOnSchemaId(@Nonnull String healthCode, @Nonnull Long createdOn, @Nonnull String schemaId) {
        Condition rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf((createdOn - CREATED_ON_OFFSET_MILLIS)))
                        , new AttributeValue().withN(String.valueOf((createdOn + CREATED_ON_OFFSET_MILLIS))));

        DynamoHealthDataRecord queryRecord = new DynamoHealthDataRecord();
        queryRecord.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoHealthDataRecord> expression = new DynamoDBQueryExpression<DynamoHealthDataRecord>()
                .withConsistentRead(false)
                .withHashKeyValues(queryRecord)
                .withRangeKeyCondition("createdOn", rangeKeyCondition)
                .withLimit(BridgeConstants.DUPE_RECORDS_MAX_COUNT);

        List<DynamoHealthDataRecord> recordList = mapper.query(DynamoHealthDataRecord.class, expression);

        // Filter out schemas that don't match and convert it to a list of the parent type.
        return recordList.stream().filter(record -> schemaId.equals(record.getSchemaId())).collect(
                Collectors.toList());
    }
}
