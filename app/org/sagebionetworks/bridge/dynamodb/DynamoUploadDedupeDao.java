package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.schema.UploadSchemaKey;

/** DDB implementation of UploadDedupeDao. */
@Component
public class DynamoUploadDedupeDao implements UploadDedupeDao {
    private static final int CREATED_ON_DELTA_MILLIS = 1000;

    private DynamoDBMapper mapper;

    /** UploadDedupe DDB mapper. */
    @Resource(name = "uploadDedupeDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDuplicate(long createdOn, String healthCode, UploadSchemaKey schemaKey) {
        // Hash key comes from health code and schema
        DynamoUploadDedupe hashKey = new DynamoUploadDedupe();
        hashKey.setHealthCode(healthCode);
        hashKey.setSchemaKey(schemaKey.toString());

        // range key, anything within 1 second is valid
        Condition createdOnCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(
                        new AttributeValue().withN(String.valueOf(createdOn - CREATED_ON_DELTA_MILLIS)),
                        new AttributeValue().withN(String.valueOf(createdOn + CREATED_ON_DELTA_MILLIS)));

        // make and execute query - We only care about the count.
        DynamoDBQueryExpression<DynamoUploadDedupe> query = new DynamoDBQueryExpression<DynamoUploadDedupe>()
                .withHashKeyValues(hashKey).withRangeKeyCondition("createdOn", createdOnCondition);
        int numDupes = mapper.count(DynamoUploadDedupe.class, query);

        return numDupes > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void registerUpload(long createdOn, String healthCode, UploadSchemaKey schemaKey, String uploadId) {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setCreatedDate(new LocalDate(createdOn, BridgeConstants.LOCAL_TIME_ZONE));
        dedupe.setCreatedOn(createdOn);
        dedupe.setHealthCode(healthCode);
        dedupe.setSchemaKey(schemaKey.toString());
        dedupe.setUploadId(uploadId);
        mapper.save(dedupe);
    }
}
