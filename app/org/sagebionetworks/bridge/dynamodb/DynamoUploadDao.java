package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class DynamoUploadDao implements UploadDao {

    // TODO: remove mapperOld once the migration is complete
    private DynamoDBMapper mapperOld;
    private DynamoDBMapper mapper;

    /**
     * This is the DynamoDB mapper that reads from and writes to our DynamoDB table. This is normally configured by
     * Spring.
     */
    @Resource(name = "UploadDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * This DynamoDB mapper writes to the old version of the Upload table. It co-exists with setDdbMapper until the
     * migration is complete. This is normally configured by Spring.
     */
    @Resource(name = "UploadDdbMapperOld")
    public void setDdbMapperOld(DynamoDBMapper mapperOld) {
        this.mapperOld = mapperOld;
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
        {
            DynamoUpload2 key = new DynamoUpload2();
            key.setUploadId(uploadId);
            DynamoUpload2 upload = mapper.load(key);
            if (upload != null) {
                return upload;
            }
        }

        // Fall back to DynamoUpload
        {
            DynamoUpload keyOld = new DynamoUpload();
            keyOld.setUploadId(uploadId);
            DynamoUpload uploadOld = mapperOld.load(keyOld);
            if (uploadOld != null) {
                return uploadOld;
            }
        }

        throw new BridgeServiceException(String.format("Upload ID %s not found", uploadId), HttpStatus.SC_NOT_FOUND);
    }

    /** {@inheritDoc} */
    @Override
    public void uploadComplete(@Nonnull Upload upload) {
        if (upload instanceof DynamoUpload2) {
            DynamoUpload2 upload2 = (DynamoUpload2) upload;
            upload2.setStatus(UploadStatus.VALIDATION_IN_PROGRESS);

            // TODO: If we globalize Bridge, we'll need to make this timezone configurable.
            upload2.setUploadDate(LocalDate.now(DateTimeZone.forID(("America/Los_Angeles"))));
            mapper.save(upload2);
        } else {
            DynamoUpload uploadOld = (DynamoUpload) upload;
            uploadOld.setComplete(true);
            mapperOld.save(uploadOld);
        }
    }
}
