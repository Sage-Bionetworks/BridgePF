package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

public class DynamoUploadDao implements UploadDao {

    // TODO: remove mapperOld once the migration is complete
    private DynamoDBMapper mapperOld;
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperOldConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload.class)).build();
        mapperOld = new DynamoDBMapper(client, mapperOldConfig);

        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload2.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public String createUpload(UploadRequest uploadRequest, String healthCode) {
        checkNotNull(uploadRequest, "Upload request is null");
        checkArgument(StringUtils.isNotBlank(healthCode), "Health code is null or blank");        

        // Always write new uploads to the new upload table.
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, healthCode);
        mapper.save(upload);
        return upload.getUploadId();
    }

    // TODO: Cache this, or make it so that calling getUpload() and uploadComplete() in sequence don't cause duplicate
    // calls to DynamoDB.
    @Override
    public Upload getUpload(String uploadId) {
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

    @Override
    public void uploadComplete(String uploadId) {
        Upload upload = getUpload(uploadId);

        if (upload instanceof DynamoUpload2) {
            DynamoUpload2 upload2 = (DynamoUpload2) upload;
            upload2.setComplete(true);

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
