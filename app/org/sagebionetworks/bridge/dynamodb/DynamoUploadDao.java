package org.sagebionetworks.bridge.dynamodb;

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
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUpload.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public String createUpload(UploadRequest uploadRequest, String healthCode) {
        DynamoUpload upload = new DynamoUpload(uploadRequest, healthCode);
        mapper.save(upload);
        return upload.getUploadId();
    }

    @Override
    public void uploadComplete(String uploadId) {
        DynamoUpload upload = new DynamoUpload();
        upload.setUploadId(uploadId);
        upload = mapper.load(upload);
        upload.setComplete(true);
        mapper.save(upload);
    }
}
