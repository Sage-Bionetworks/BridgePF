package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthIdDao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoHealthIdDao implements HealthIdDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoHealthId.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public boolean setIfNotExist(String id, String code) {
        checkArgument(StringUtils.isNotBlank(id), "Health ID is blank or null");
        checkArgument(StringUtils.isNotBlank(code), "Health code is blank or null");
        
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
        checkArgument(StringUtils.isNotBlank(id), "Health ID is blank or null");
        
        DynamoHealthId healthId = mapper.load(DynamoHealthId.class, id);
        if (healthId != null) {
            return healthId.getCode();
        }
        return null;
    }
    
    @Override
    public void deleteMapping(String healthCode) {
        checkArgument(isNotEmpty(healthCode));
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(healthCode));
        scan.addFilterCondition("code", condition);
        
        List<DynamoHealthId> mappings = mapper.scan(DynamoHealthId.class, scan);
        List<FailedBatch> failures = mapper.batchDelete(mappings);
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
