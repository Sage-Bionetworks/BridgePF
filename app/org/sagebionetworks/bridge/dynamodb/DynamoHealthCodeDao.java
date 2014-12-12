package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoHealthCodeDao implements HealthCodeDao {

    private final Logger logger = LoggerFactory.getLogger(DynamoHealthCodeDao.class);

    private DynamoDBMapper mapper;
    private DynamoDBMapper mapper2;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoHealthCode.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
        DynamoDBMapperConfig mapperConfig2 = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoHealthCode2.class)).build();
        mapper2 = new DynamoDBMapper(client, mapperConfig2);
    }

    @Override
    public boolean setIfNotExist(String code, String studyId) {
        checkArgument(isNotBlank(code));
        checkArgument(isNotBlank(studyId));
        boolean saved = false;
        try {
            DynamoHealthCode toSave = new DynamoHealthCode(code);
            mapper.save(toSave);
            saved = true;
        } catch(ConditionalCheckFailedException e) {
            saved = false;
        }
        try {
            DynamoHealthCode2 toSave2 = new DynamoHealthCode2(code, studyId);
            mapper2.save(toSave2);
        } catch (Throwable e) {
            logger.error("Cannot save health code into the new schema", e);
        }
        return saved;
    }

    @Override
    public String getStudyIdentifier(String code) {
        DynamoHealthCode2 key = new DynamoHealthCode2(code);
        DynamoHealthCode2 loaded = mapper2.load(key);
        if (loaded == null) {
            return null;
        }
        return loaded.getStudyIdentifier();
    }
}
