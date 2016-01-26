package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;

/** DDB implementation of UploadDedupe. */
@DynamoThroughput(readCapacity=5, writeCapacity=10)
@DynamoDBTable(tableName = "UploadDedupe")
public class DynamoUploadDedupe {
    private LocalDate createdDate;
    private long createdOn;
    private String healthCode;
    private String schemaKey;
    private String uploadId;

    /**
     * Calendar date the upload was created, as recorded using the server's local time zone (Seattle time). This is
     * used to clean up old upload dedupe entries afterwards.
     */
    @DynamoDBIndexHashKey(attributeName = "createdDate", globalSecondaryIndexName = "createdDate-index")
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    public LocalDate getCreatedDate() {
        return createdDate;
    }

    /** @see #getCreatedDate */
    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    /** Timestamp when the upload was created, as reported by the app. This is used as the range key for de-duping. */
    @DynamoDBRangeKey
    public long getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    /** DDB hash key, which is the concatenation of the health code and the schema. */
    @DynamoDBHashKey
    public String getDdbKey() {
        Preconditions.checkArgument(StringUtils.isNotBlank(healthCode), "healthCode must be specified");
        Preconditions.checkArgument(schemaKey != null, "schemaKey must be specified");
        return healthCode + ":" + schemaKey;
    }

    /** Sets the DDB key. Generally only called by the DDB mapper. */
    public void setDdbKey(String ddbKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(ddbKey), "ddbKey must be specified");

        String[] parts = ddbKey.split(":", 2);
        Preconditions.checkArgument(parts.length == 2, "ddbKey has wrong number of parts");
        Preconditions.checkArgument(StringUtils.isNotBlank(parts[0]), "ddbKey must contain healthCode");
        Preconditions.checkArgument(StringUtils.isNotBlank(parts[1]), "ddbKey must contain schemaKey");

        this.healthCode = parts[0];
        this.schemaKey = parts[1];
    }

    /** Health code of uploading user, part of the hash key. */
    @DynamoDBIgnore
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Schema of the upload, part of the hash key. */
    @DynamoDBIgnore
    public String getSchemaKey() {
        return schemaKey;
    }

    /** @see #getSchemaKey */
    public void setSchemaKey(String schemaKey) {
        this.schemaKey = schemaKey;
    }

    /** ID of upload, used to key into the Uploads table during offline analysis. */
    public String getUploadId() {
        return uploadId;
    }

    /** @see #getUploadId */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }
}
