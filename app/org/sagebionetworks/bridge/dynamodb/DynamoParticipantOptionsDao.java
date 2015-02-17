package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

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
import com.google.common.collect.Maps;

public class DynamoParticipantOptionsDao implements ParticipantOptionsDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoParticipantOptions.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, Option option, String value) {
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
    public String getOption(String healthCode, Option option) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        String value = option.getDefaultValue();
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null && options.getOptions().get(option.name()) != null) {
            value = options.getOptions().get(option.name());
        }
        return value;
    }
    
    @Override
    public void deleteAllParticipantOptions(String healthCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            mapper.delete(options);    
        }
    }

    @Override
    public void deleteOption(String healthCode, Option option) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            options.getOptions().remove(option.name());
            mapper.save(options);
        }
    }

    @Override
    public Map<Option,String> getAllParticipantOptions(String healthCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        Map<Option,String> map = Maps.newHashMap();
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            for (Option opt : Option.values()) {
                map.put(opt, null);
            }
        } else {
            for (Option opt : Option.values()) {
                String value = opt.getDefaultValue();
                if (options.getOptions().get(opt.name()) != null) {
                    value = options.getOptions().get(opt.name());
                }
                map.put(opt, value);
            }
        }
        return map;
    }
    
    @Override
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, Option option) {
        // The only place we need the study, and that's to find all the options for all the 
        // participants in a given study.
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        List<FailedBatch> failures = mapper.batchDelete(mappings);
        BridgeUtils.ifFailuresThrowException(failures);
        
        OptionLookup map = new OptionLookup(option.getDefaultValue());
        for (DynamoParticipantOptions opt : mappings) {
            map.put(opt.getHealthCode(), opt.getOptions().get(option.name()));
        }
        return map;
    }

}
