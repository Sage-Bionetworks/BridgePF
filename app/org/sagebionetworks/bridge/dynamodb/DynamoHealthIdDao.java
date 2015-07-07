package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

@Component
public class DynamoHealthIdDao implements HealthIdDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(DynamoUtils.getTableNameOverride(DynamoHealthId.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public boolean setIfNotExist(String id, String code) {
        checkArgument(isNotBlank(id), "Health ID is blank or null");
        checkArgument(isNotBlank(code), "Health code is blank or null");
        try {
            DynamoHealthId toSave = new DynamoHealthId();
            toSave.setId(id);
            toSave.setCode(code);
            mapper.save(toSave);
            return true;
        } catch(ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public String getCode(String id) {
        checkArgument(isNotBlank(id), "Health ID is blank or null");
        DynamoHealthId healthId = mapper.load(DynamoHealthId.class, id);
        if (healthId != null) {
            return healthId.getCode();
        }
        return null;
    }
}
