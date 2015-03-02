package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachmentBuilder;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment}. */
@DynamoDBTable(tableName = "HealthDataAttachment")
public class DynamoHealthDataAttachment implements HealthDataAttachment {
    private String id;
    private String recordId;
    private Long version;

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    /** @see #getId */
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "recordId", globalSecondaryIndexName = "recordId-index")
    @Override
    public String getRecordId() {
        return recordId;
    }

    /** @see #getRecordId */
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /** {@inheritDoc} */
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataAttachmentBuilder}. */
    public static class Builder extends HealthDataAttachmentBuilder {
        @Override
        protected HealthDataAttachment buildUnvalidated() {
            DynamoHealthDataAttachment attachment = new DynamoHealthDataAttachment();
            attachment.setId(getId());
            attachment.setRecordId(getRecordId());
            attachment.setVersion(getVersion());
            return attachment;
        }
    }
}
