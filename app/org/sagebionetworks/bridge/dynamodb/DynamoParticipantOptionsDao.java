package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
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
import com.google.common.collect.Maps;

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
    public String getOption(String healthCode, ParticipantOption option) {
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
    public Map<ParticipantOption,OptionLookup> getAllOptionsForAllStudyParticipants(StudyIdentifier studyIdentifier) {
        // For each option type, create a map for that to an OptionLookup that allows you to retrieve the value 
        // by healthcode.
        Map<ParticipantOption,OptionLookup> map = Maps.newHashMap();
        for (ParticipantOption optionType : ParticipantOption.values()) {
            map.put(optionType, new OptionLookup(optionType.getDefaultValue()));
        }
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        
        for (DynamoParticipantOptions mapping : mappings) {
            for (ParticipantOption optionType : ParticipantOption.values()) {
                String defaultValue = optionType.getDefaultValue();
                String value = mapping.getOptions().get(optionType.name());
                map.get(optionType).put(mapping.getHealthCode(), (value != null) ? value : defaultValue);
            } 
        }
        return map;
    }
    
    @Override
    public Map<ParticipantOption,String> getAllParticipantOptions(String healthCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        Map<ParticipantOption,String> map = Maps.newHashMap();
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            return map;
        }
        for (ParticipantOption opt : ParticipantOption.values()) {
            String value = opt.getDefaultValue();
            if (options.getOptions().get(opt.name()) != null) {
                value = options.getOptions().get(opt.name());
            }
            map.put(opt, value);
        }        
        return map;
    }
    
    @Override
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option) {
        // The only place we need the study, and that's to find all the options for all the 
        // participants in a given study.
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        
        OptionLookup map = new OptionLookup(option.getDefaultValue());
        for (DynamoParticipantOptions mapping : mappings) {
            map.put(mapping.getHealthCode(), mapping.getOptions().get(option.name()));
        }
        return map;
    }

}
