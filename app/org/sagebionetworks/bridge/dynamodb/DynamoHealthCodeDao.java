package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoHealthCode.class)).build();
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

    @Override
    public void setStudyId(String code, String studyId) {
        checkArgument(isNotBlank(code));
        checkArgument(isNotBlank(studyId));
        DynamoHealthCode key = new DynamoHealthCode();
        key.setCode(code);
        DynamoHealthCode loaded = mapper.load(key);
        if (loaded == null) {
            throw new RuntimeException("Can't find health code in DynamoDB");
        }
        String oldStudyId = loaded.getStudyIdentifier();
        if (oldStudyId == null) {
            loaded.setStudyIdentifier(studyId);
            mapper.save(loaded);
        } else if (!oldStudyId.equals(studyId)) {
            throw new RuntimeException("DynamoDB has a different study ID for the health code");
        }
    }
}
