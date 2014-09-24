package org.sagebionetworks.bridge.dynamodb;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.Upload;
import org.sagebionetworks.bridge.models.UploadRequest;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "Upload")
public class DynamoUpload implements Upload, DynamoTable {

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
        uploadId = UUID.randomUUID().toString();
        timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        objectId = UUID.randomUUID().toString();
        this.healthCode = healthCode;
        complete = false;
        name = uploadRequest.getName();
        contentLength = uploadRequest.getContentLength();
        contentType = uploadRequest.getContentType();
        contentMd5 = uploadRequest.getContentMd5();
    }

    @DynamoDBHashKey
    @Override
    public String getUploadId() {
        return uploadId;
    }
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    @DynamoDBAttribute
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @DynamoDBAttribute
    @Override
    public String getObjectId() {
        return objectId;
    }
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    @DynamoDBAttribute
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @DynamoDBAttribute
    @Override
    public boolean isComplete() {
        return complete;
    }
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    @DynamoDBAttribute
    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @DynamoDBAttribute
    @Override
    public long getContentLength() {
        return contentLength;
    }
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    @DynamoDBAttribute
    @Override
    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @DynamoDBAttribute
    @Override
    public String getContentMd5() {
        return contentMd5;
    }
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
}
