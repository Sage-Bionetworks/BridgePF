package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.dao.HealthDataDao}. */
@Component
public class DynamoHealthDataDao implements HealthDataDao {
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
    @Resource(name = "healthDataHealthCodeCreatedOnIndex")
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
        // query for the keys we need to delete. The index returns all fields which are
        // not correctly deserialized by IndexHelper, so do it manually.
        Index index = healthCodeIndex.getIndex();
        Iterable<Item> iter = index.query("healthCode", healthCode);
        
        List<DynamoHealthDataRecord> keysToDelete = Lists.newArrayList();
        for (Item item : iter) {
            DynamoHealthDataRecord oneRecord = new DynamoHealthDataRecord();
            oneRecord.setId(item.getString("id"));
            keysToDelete.add(oneRecord);
        }

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
    public List<HealthDataRecord> getRecordsByHealthCodeCreatedOn(String healthCode, long createdOnStart,
            long createdOnEnd) {
        // Hash key.
        DynamoHealthDataRecord queryRecord = new DynamoHealthDataRecord();
        queryRecord.setHealthCode(healthCode);

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(createdOnStart)),
                        new AttributeValue().withN(String.valueOf(createdOnEnd)));

        // Construct query.
        DynamoDBQueryExpression<DynamoHealthDataRecord> expression =
                new DynamoDBQueryExpression<DynamoHealthDataRecord>()
                        .withConsistentRead(false)
                        .withHashKeyValues(queryRecord)
                        .withRangeKeyCondition("createdOn", rangeKeyCondition)
                        .withLimit(BridgeConstants.DUPE_RECORDS_MAX_COUNT);

        // Execute query.
        QueryResultPage<DynamoHealthDataRecord> resultPage = mapper.queryPage(DynamoHealthDataRecord.class, expression);
        List<DynamoHealthDataRecord> recordList = resultPage.getResults();

        return ImmutableList.copyOf(recordList);
    }
}
