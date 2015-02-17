package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

@DynamoThroughput(writeCapacity=50, readCapacity=50)
@DynamoDBTable(tableName = "Upload")
public class DynamoUpload implements Upload {

    private String uploadId;
    private long timestamp;
    private String objectId;
    private String healthCode;
    private boolean complete;
    private Long version;
    private String name;
    private long contentLength;
    private String contentType;
    private String contentMd5;

    public DynamoUpload() {}

    public DynamoUpload(UploadRequest uploadRequest, String healthCode) {
        uploadId = BridgeUtils.generateGuid();
        timestamp = DateUtils.getCurrentMillisFromEpoch();
        objectId = BridgeUtils.generateGuid();
        this.healthCode = healthCode;
        complete = false;
        name = uploadRequest.getName();
        contentLength = uploadRequest.getContentLength();
        contentType = uploadRequest.getContentType();
        contentMd5 = uploadRequest.getContentMd5();
    }

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getUploadId() {
        return uploadId;
    }
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    @DynamoDBAttribute
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public LocalDate getUploadDate() {
        // UploadDate is a synonym for timestamp, except flattened to a calendar date in Pacific local time.
        return new LocalDate(timestamp, DateTimeZone.forID(("America/Los_Angeles")));
    }

    /** {@inheritDoc} */
    @DynamoDBAttribute
    @Override
    public String getObjectId() {
        return objectId;
    }
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    /** {@inheritDoc} */
    @DynamoDBAttribute
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @DynamoDBAttribute
    public boolean isComplete() {
        return complete;
    }
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public boolean canBeValidated() {
        // For DynamoUpload uploads, as long as we haven't set the complete flag, we can call uploadComplete() on this
        // upload and kick off validation.
        return !complete;
    }

    /** DynamoUpload does not support getStatus. This method will always return UploadStatus.UNKNOWN. */
    @DynamoDBIgnore
    @Override
    public UploadStatus getStatus() {
        return UploadStatus.UNKNOWN;
    }

    /** DynamoUpload does not support validation. This method will always return an empty immutable list. */
    @DynamoDBIgnore
    @Override
    public List<String> getValidationMessageList() {
        return Collections.emptyList();
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    @DynamoDBAttribute
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public String getFilename() {
        // This is a synonym for getName().
        return name;
    }

    @DynamoDBAttribute
    public long getContentLength() {
        return contentLength;
    }
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    @DynamoDBAttribute
    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @DynamoDBAttribute
    public String getContentMd5() {
        return contentMd5;
    }
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
}
