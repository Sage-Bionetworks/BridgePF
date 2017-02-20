package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;

/** DDB implementation of UploadDedupe. */
@DynamoThroughput(readCapacity=5, writeCapacity=10)
@DynamoDBTable(tableName = "UploadDedupe2")
public class DynamoUploadDedupe {
    private String healthCode;
    private String originalUploadId;
    private String uploadMd5;
    private LocalDate uploadRequestedDate;
    private long uploadRequestedOn;

    /** DDB hash key, which is the concatenation of the health code and the upload MD5. */
    @DynamoDBHashKey
    public String getDdbKey() {
        Preconditions.checkArgument(StringUtils.isNotBlank(healthCode), "healthCode must be specified");
        Preconditions.checkArgument(StringUtils.isNotBlank(uploadMd5), "uploadMd5 must be specified");
        return healthCode + ":" + uploadMd5;
    }

    /** Sets the DDB key. Generally only called by the DDB mapper. */
    public void setDdbKey(String ddbKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(ddbKey), "ddbKey must be specified");

        String[] parts = ddbKey.split(":", 2);
        Preconditions.checkArgument(parts.length == 2, "ddbKey has wrong number of parts");
        Preconditions.checkArgument(StringUtils.isNotBlank(parts[0]), "ddbKey must contain healthCode");
        Preconditions.checkArgument(StringUtils.isNotBlank(parts[1]), "ddbKey must contain uploadMd5");

        this.healthCode = parts[0];
        this.uploadMd5 = parts[1];
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

    /** ID of original upload, used to key into the Uploads table during offline analysis. */
    public String getOriginalUploadId() {
        return originalUploadId;
    }

    /** @see #getOriginalUploadId */
    public void setOriginalUploadId(String originalUploadId) {
        this.originalUploadId = originalUploadId;
    }

    /** Upload's MD5, used to determine if upload content is different. */
    @DynamoDBIgnore
    public String getUploadMd5() {
        return uploadMd5;
    }

    /** @see #getUploadMd5 */
    public void setUploadMd5(String uploadMd5) {
        this.uploadMd5 = uploadMd5;
    }

    /**
     * Calendar date the upload was requested on, as recorded using the server's local time zone (Seattle time). This
     * is used to clean up old upload dedupe entries afterwards.
     */
    @DynamoDBIndexHashKey(attributeName = "uploadRequestedDate",
            globalSecondaryIndexName = "uploadRequestedDate-index")
    @DynamoDBTypeConverted(converter = LocalDateMarshaller.class)
    @SuppressWarnings("unused")
    public LocalDate getUploadRequestedDate() {
        return uploadRequestedDate;
    }

    /** @see #getUploadRequestedDate */
    public void setUploadRequestedDate(LocalDate uploadRequestedDate) {
        this.uploadRequestedDate = uploadRequestedDate;
    }

    /**
     * Epoch millisecond timestamp when this upload was requested, used as part of the range key. Since MD5s can have
     * collisions, we need an additional key to make sure the uploads are different.
     */
    @DynamoDBRangeKey
    @SuppressWarnings("unused")
    public long getUploadRequestedOn() {
        return uploadRequestedOn;
    }

    /** @see #getUploadRequestedOn */
    public void setUploadRequestedOn(long uploadRequestedOn) {
        this.uploadRequestedOn = uploadRequestedOn;
    }
}
