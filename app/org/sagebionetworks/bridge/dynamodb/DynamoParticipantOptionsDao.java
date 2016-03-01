package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.accounts.AllUserOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.UserOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableMap;

@Component
public class DynamoParticipantOptionsDao implements ParticipantOptionsDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(BridgeConfig bridgeConfig, AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(DynamoUtils.getTableNameOverride(DynamoParticipantOptions.class, bridgeConfig)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    // Why? So we can mock the mapper.
    protected void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            options = new DynamoParticipantOptions();
        }
        options.setStudyKey(studyIdentifier.getIdentifier());
        options.setHealthCode(healthCode);
        options.getOptions().put(option.name(), value);
        mapper.save(options);
    }
    
    @Override
    public UserOptionsLookup getOptions(String healthCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            return new UserOptionsLookup(ImmutableMap.of());
        }
        return new UserOptionsLookup(options.getOptions());
    }
    
    @Override
    public void deleteAllOptions(String healthCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            mapper.delete(options);    
        }
    }

    @Override
    public void deleteOption(String healthCode, ParticipantOption option) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            options.getOptions().remove(option.name());
            mapper.save(options);
        }
    }

    @Override
    public AllUserOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier) {
        AllUserOptionsLookup allLookup = new AllUserOptionsLookup();
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        
        for (DynamoParticipantOptions mapping : mappings) {
            allLookup.put(mapping.getHealthCode(), new UserOptionsLookup(mapping.getOptions()));
        }        
        return allLookup;
    }

}
