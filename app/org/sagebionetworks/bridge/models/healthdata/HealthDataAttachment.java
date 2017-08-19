package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * Metadata record about health data attachments. This is used to keep track of attachments in external storage
 * (generally S3) and the actual health data record.
 */
@BridgeTypeName("HealthDataAttachment")
@JsonDeserialize(as = DynamoHealthDataAttachment.class)
public interface HealthDataAttachment {
    /** Convenience method to instantiate a HealthDataAttachment. */
    static HealthDataAttachment create() {
        return new DynamoHealthDataAttachment();
    }

    /**
     * Attachment ID. This is used as both the unique attachment ID and the S3 key in the attachments bucket to
     * guarantee filename uniqueness in S3.
     */
    String getId();

    /** @see #getId */
    void setId(String id);

    /** Record ID of the health data record this file is attached to. */
    String getRecordId();

    /** @see #getRecordId */
    void setRecordId(String recordId);

    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();

    /** @see #getVersion */
    void setVersion(Long version);
}
