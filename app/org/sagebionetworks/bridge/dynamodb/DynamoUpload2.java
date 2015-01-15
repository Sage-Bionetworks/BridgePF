package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;

/**
 * This DynamoDB table stores metadata for Bridge uploads. This class also defines global secondary indices, which can
 * be used for more efficient queries.
 */
@DynamoDBTable(tableName = "Upload2")
public class DynamoUpload2 implements DynamoTable, Upload {
    private boolean complete;
    private long contentLength;
    private String contentMd5;
    private String contentType;
    private String filename;
    private String healthCode;
    private LocalDate uploadDate;
    private String uploadId;
    private Long version;

    /** This empty constructor is needed by the DynamoDB mapper. */
    public DynamoUpload2() {}

    /** Construct a DDB Upload object from an upload request and healthcode. */
    public DynamoUpload2(UploadRequest uploadRequest, String healthCode) {
        complete = false;
        contentLength = uploadRequest.getContentLength();
        contentType = uploadRequest.getContentType();
        contentMd5 = uploadRequest.getContentMd5();
        filename = uploadRequest.getName();
        this.healthCode = healthCode;
        uploadId = BridgeUtils.generateGuid();
    }

    /** True if the upload is complete. False if not. */
    @Override
    public boolean isComplete() {
        return complete;
    }

    /** @see #isComplete */
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /** Upload content length in bytes. */
    public long getContentLength() {
        return contentLength;
    }

    /** @see #getContentLength */
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    /** The base64-encoded, 128-bit MD5 digest of the object body. */
    public String getContentMd5() {
        return contentMd5;
    }

    /** @see #getContentMd5 */
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }

    /** MIME content type. */
    public String getContentType() {
        return contentType;
    }

    /** @see #getContentType */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /** Name of the file to upload. */
    @DynamoDBIndexHashKey(attributeName = "filename", globalSecondaryIndexName = "filename-index")
    public String getFilename() {
        return filename;
    }

    /** @see #getFilename */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /** Health code of the user from which this upload originates from. */
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-index")
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /**
     * Calendar date the file was uploaded (specifically, the uploadComplete() call. Date is determined using Pacific
     * local time.
     */
    @DynamoDBIndexHashKey(attributeName = "uploadDate", globalSecondaryIndexName = "uploadDate-index")
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    public LocalDate getUploadDate() {
        return uploadDate;
    }

    /** @see #getUploadDate */
    public void setUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
    }

    /**
     * Unique ID for the upload. This is used both as a DynamoDB key and as an S3 filename. We generate a guid for the
     * S3 filename to avoid hotspots in S3 from poorly distributed filenames.
     */
    @DynamoDBHashKey
    @Override
    public String getUploadId() {
        return uploadId;
    }

    // TODO: In the new implementation, this is the same as upload ID. After the migration, remove this method.
    @Override
    @DynamoDBIgnore
    public String getObjectId() {
        return uploadId;
    }

    /** @see #getUploadId */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /** DynamoDB version, used for optimistic locking */
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }
}
