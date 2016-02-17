package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;

/** DDB implementation of UploadDedupeDao. */
@Component
public class DynamoUploadDedupeDao implements UploadDedupeDao {
    private static final int NUM_DAYS_BEFORE = 7;

    private DynamoDBMapper mapper;

    /** UploadDedupe DDB mapper. */
    @Resource(name = "uploadDedupeDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public String getDuplicate(String healthCode, String uploadMd5, DateTime uploadRequestedOn) {
        // Hash key comes from health code and upload MD5
        DynamoUploadDedupe hashKey = new DynamoUploadDedupe();
        hashKey.setHealthCode(healthCode);
        hashKey.setUploadMd5(uploadMd5);

        // MD5s can collide. So as an extra check for duplicate values, we only look at uploads requested within a
        // certain time. Since apps are known to upload a file, then upload the same file the next day, we'll give it
        // a 7-day buffer period for finding dupes.
        DateTime dupeWindowStartTime = uploadRequestedOn.minusDays(NUM_DAYS_BEFORE);
        Condition requestedOnCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(dupeWindowStartTime.getMillis())),
                        new AttributeValue().withN(String.valueOf(uploadRequestedOn.getMillis())));

        // make and execute query
        DynamoDBQueryExpression<DynamoUploadDedupe> query = new DynamoDBQueryExpression<DynamoUploadDedupe>()
                .withHashKeyValues(hashKey).withRangeKeyCondition("uploadRequestedOn", requestedOnCondition);
        List<DynamoUploadDedupe> dedupeList = mapper.query(DynamoUploadDedupe.class, query);

        if (dedupeList.isEmpty()) {
            return null;
        } else {
            return dedupeList.get(0).getOriginalUploadId();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerUpload(String healthCode, String uploadMd5, DateTime uploadRequestedOn,
            String originalUploadId) {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setHealthCode(healthCode);
        dedupe.setOriginalUploadId(originalUploadId);
        dedupe.setUploadMd5(uploadMd5);
        dedupe.setUploadRequestedDate(uploadRequestedOn.withZone(BridgeConstants.LOCAL_TIME_ZONE).toLocalDate());
        dedupe.setUploadRequestedOn(uploadRequestedOn.getMillis());
        mapper.save(dedupe);
    }
}
