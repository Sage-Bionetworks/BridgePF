package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.dao.HealthCodeDao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoHealthCodeDao implements HealthCodeDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoHealthCode.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    public boolean setIfNotExist(String code) {
        try {
            DynamoHealthCode toSave = new DynamoHealthCode(code);
            mapper.save(toSave);
            return true;
        } catch(ConditionalCheckFailedException e) {
            return false;
        }
    }
}
