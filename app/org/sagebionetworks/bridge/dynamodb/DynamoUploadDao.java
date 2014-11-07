package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.UploadRequest;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;

public class DynamoUploadDao implements UploadDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public String createUpload(UploadRequest uploadRequest, String healthCode) {
        checkNotNull(uploadRequest, "Upload request is null");
        checkArgument(StringUtils.isNotBlank(healthCode), "Health code is null or blank");        
        
        DynamoUpload upload = new DynamoUpload(uploadRequest, healthCode);
        mapper.save(upload);
        return upload.getUploadId();
    }

    @Override
    public String getObjectId(String uploadId) {
        checkArgument(StringUtils.isNotBlank(uploadId), "Upload ID is null or blank");
        
        DynamoUpload upload = new DynamoUpload();
        upload.setUploadId(uploadId);
        upload = mapper.load(upload);
        return upload.getObjectId();
    }

    @Override
    public void uploadComplete(String uploadId) {
        checkArgument(StringUtils.isNotBlank(uploadId), "Upload ID is null or blank");
        
        DynamoUpload upload = new DynamoUpload();
        upload.setUploadId(uploadId);
        upload = mapper.load(upload);
        upload.setComplete(true);
        mapper.save(upload);
    }

    @Override
    public boolean isComplete(String uploadId) {
        checkArgument(StringUtils.isNotBlank(uploadId), "Upload ID is null or blank");
        
        DynamoUpload upload = new DynamoUpload();
        upload.setUploadId(uploadId);
        upload = mapper.load(upload);
        return upload != null && upload.isComplete();
    }
}
