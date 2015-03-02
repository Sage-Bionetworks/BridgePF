package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataAttachmentDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachmentBuilder;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.dao.HealthDataAttachmentDao}. */
@Component
public class DynamoHealthDataAttachmentDao implements HealthDataAttachmentDao {
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataAttachment table. This is configured by Spring. */
    @Resource(name = "healthDataAttachmentDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public String createOrUpdateAttachment(@Nonnull HealthDataAttachment attachment) {
        DynamoHealthDataAttachment dynamoAttachment = (DynamoHealthDataAttachment) attachment;

        if (StringUtils.isBlank(dynamoAttachment.getId())) {
            // The attachment doesn't have its ID assigned yet (new attachment). Create an ID and assign it.
            String id = BridgeUtils.generateGuid();
            dynamoAttachment.setId(id);
        }

        // persist to DDB
        mapper.save(dynamoAttachment);
        return dynamoAttachment.getId();
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataAttachmentBuilder getRecordBuilder() {
        return new DynamoHealthDataAttachment.Builder();
    }
}
