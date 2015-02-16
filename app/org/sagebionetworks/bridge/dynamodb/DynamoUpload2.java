package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.google.common.collect.ImmutableList;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

/**
 * This DynamoDB table stores metadata for Bridge uploads. This class also defines global secondary indices, which can
 * be used for more efficient queries.
 */
@DynamoThroughput(writeCapacity=50, readCapacity=50)
@DynamoDBTable(tableName = "Upload2")
public class DynamoUpload2 implements Upload {
    private long contentLength;
    private String contentMd5;
    private String contentType;
    private String filename;
    private String healthCode;
    private UploadStatus status;
    private LocalDate uploadDate;
    private String uploadId;
    private final List<String> validationMessageList = new ArrayList<>();
    private Long version;

    /** This empty constructor is needed by the DynamoDB mapper. */
    public DynamoUpload2() {}

    /** Construct a DDB Upload object from an upload request and healthcode. */
    public DynamoUpload2(UploadRequest uploadRequest, String healthCode) {
        contentLength = uploadRequest.getContentLength();
        contentType = uploadRequest.getContentType();
        contentMd5 = uploadRequest.getContentMd5();
        filename = uploadRequest.getName();
        this.healthCode = healthCode;
        status = UploadStatus.REQUESTED;
        uploadId = BridgeUtils.generateGuid();
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public boolean canBeValidated() {
        // The only status that can be validated is REQUESTED. Once validation happens, the status moves to
        // VALIDATION_IN_PROGRESS, and the user can no longer call uploadComplete() to kick off validation.
        return status == UploadStatus.REQUESTED;
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

    /** {@inheritDoc} */
    @Override
    public String getFilename() {
        return filename;
    }

    /** @see #getFilename */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-index")
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public String getObjectId() {
        // In DynamoUpload2, object ID and upload ID are the same.
        return uploadId;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = EnumMarshaller.class)
    @Override
    public UploadStatus getStatus() {
        return status;
    }

    /** @see #getStatus */
    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    @Override
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

    /** @see #getUploadId */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * Returns an immutable copy of the validation message list. Changes to the underlying message list will not be
     * reflected in this copy. Note that the validation message list will be non-null and will not contain null
     * messages.
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    @Override
    public @Nonnull List<String> getValidationMessageList() {
        return ImmutableList.copyOf(validationMessageList);
    }

    /**
     * <p>
     * Sets the validation message list. Internally, this clears all messages from the internal validation message
     * list, then copies all elements from the given validation message list. Note that the validation message list
     * must be non-null and must not contain null messages.
     * </p>
     * <p>
     * The DynamoDB mapper needs this method, and it needs it to take a List of String.
     * </p>
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    public void setValidationMessageList(@Nonnull List<String> validationMessageList) {
        this.validationMessageList.clear();
        appendValidationMessages(validationMessageList);
    }

    /**
     * Appends validation messages to the validation message list. Note that the validation message list must be
     * non-null and must not contain null messages.
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    public void appendValidationMessages(@Nonnull Collection<? extends String> validationMessageList) {
        this.validationMessageList.addAll(validationMessageList);
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
