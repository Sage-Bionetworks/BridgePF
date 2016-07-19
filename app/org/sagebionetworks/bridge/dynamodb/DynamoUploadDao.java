package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@Component
public class DynamoUploadDao implements UploadDao {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoUploadDao.class);

    private DynamoDBMapper mapper;

    /**
     * This is the DynamoDB mapper that reads from and writes to our DynamoDB table. This is normally configured by
     * Spring.
     */
    @Resource(name = "uploadDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Upload createUpload(@Nonnull UploadRequest uploadRequest, @Nonnull String healthCode) {
        checkNotNull(uploadRequest, "Upload request is null");
        checkArgument(StringUtils.isNotBlank(healthCode), "Health code is null or blank");        

        // Always write new uploads to the new upload table.
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, healthCode);
        mapper.save(upload);
        return upload;
    }

    // TODO: Cache this, or make it so that calling getUpload() and uploadComplete() in sequence don't cause duplicate
    // calls to DynamoDB.
    /** {@inheritDoc} */
    @Override
    public Upload getUpload(@Nonnull String uploadId) {
        // Fetch upload from DynamoUpload2
        DynamoUpload2 key = new DynamoUpload2();
        key.setUploadId(uploadId);
        DynamoUpload2 upload = mapper.load(key);
        if (upload != null) {
            return upload;
        }

        throw new NotFoundException(String.format("Upload ID %s not found", uploadId));
    }

    /** {@inheritDoc} */
    @Override
    public void uploadComplete(@Nonnull Upload upload) {
        DynamoUpload2 upload2 = (DynamoUpload2) upload;

        upload2.setStatus(UploadStatus.VALIDATION_IN_PROGRESS);

        // TODO: If we globalize Bridge, we'll need to make this timezone configurable.
        upload2.setUploadDate(LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE));
        try {
            mapper.save(upload2);
        } catch (ConditionalCheckFailedException ex) {
            // the only other modification of upload object is during validation, so this upload must have been already marked complete
            LOG.info("Concurrent modification of upload " + upload.getUploadId() + " while marking upload complete");
        }
    }

    /**
     * Writes validation status and appends messages to Dynamo DB. Only DynamoUpload2 objects can have status and
     * validation. DynamoUpload objects will be ignored.
     *
     * @see org.sagebionetworks.bridge.dao.UploadDao#writeValidationStatus
     */
    @Override
    public void writeValidationStatus(@Nonnull Upload upload, @Nonnull UploadStatus status,
            @Nonnull List<String> validationMessageList, String recordId) {
        // set status and append messages
        DynamoUpload2 upload2 = (DynamoUpload2) upload;
        upload2.setStatus(status);
        upload2.appendValidationMessages(validationMessageList);
        upload2.setRecordId(recordId);

        // persist
        mapper.save(upload2);
    }
}
