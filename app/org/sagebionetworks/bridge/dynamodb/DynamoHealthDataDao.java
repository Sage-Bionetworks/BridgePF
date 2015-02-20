package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
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
    public String createRecord(@Nonnull HealthDataRecord record) {
        // create record ID
        String id = BridgeUtils.generateGuid();
        DynamoHealthDataRecord dynamoRecord = (DynamoHealthDataRecord) record;
        dynamoRecord.setId(id);

        // persist to DDB
        mapper.save(dynamoRecord);
        return id;
    }

    /** {@inheritDoc} */
    public int deleteRecordsForHealthCode(@Nonnull String healthCode) {
        // query for the keys we need to delete
        List<HealthDataRecord> keysToDelete = healthCodeIndex.queryKeys(HealthDataRecord.class, "healthCode",
                healthCode);

        // and then delete
        List<DynamoDBMapper.FailedBatch> failureList = mapper.batchDelete(keysToDelete);
        BridgeUtils.ifFailuresThrowException(failureList);

        return keysToDelete.size();
    }

    /** {@inheritDoc} */
    @Override
    public List<HealthDataRecord> getRecordsForUploadDate(@Nonnull String uploadDate) {
        return uploadDateIndex.query(HealthDataRecord.class, "uploadDate", uploadDate);
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataRecordBuilder getRecordBuilder() {
        return new DynamoHealthDataRecord.Builder();
    }
}
