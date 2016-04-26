package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoHealthCodeDao implements HealthCodeDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoHealthCode.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public boolean setIfNotExist(String code, String studyId) {
        checkArgument(isNotBlank(code));
        checkArgument(isNotBlank(studyId));
        try {
            DynamoHealthCode toSave = new DynamoHealthCode(code, studyId);
            mapper.save(toSave);
            return true;
        } catch(ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public String getStudyIdentifier(final String code) {
        DynamoHealthCode key = new DynamoHealthCode();
        key.setCode(code);
        DynamoHealthCode loaded = mapper.load(key);
        if (loaded == null) {
            return null;
        }
        return loaded.getStudyIdentifier();
    }
}
