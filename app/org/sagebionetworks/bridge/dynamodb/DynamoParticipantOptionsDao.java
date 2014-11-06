package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.Study;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Maps;

public class DynamoParticipantOptionsDao implements ParticipantOptionsDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoParticipantOptions.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void setOption(Study study, String healthDataCode, Option option, String value) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthDataCode(healthDataCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            options = new DynamoParticipantOptions();
        }
        options.setStudyKey(study.getKey());
        options.setHealthDataCode(healthDataCode);
        options.getOptions().put(option.name(), value);
        mapper.save(options);
    }
    
    @Override
    public String getOption(String healthDataCode, Option option) {
        return getOption(healthDataCode, option, null);
    }

    @Override
    public String getOption(String healthDataCode, Option option, String defaultValue) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthDataCode(healthDataCode);
        
        String value = defaultValue;
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null && options.getOptions().get(option.name()) != null) {
            value = options.getOptions().get(option.name());
        }
        return value;
    }
    
    @Override
    public void deleteAllParticipantOptions(String healthDataCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthDataCode(healthDataCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            mapper.delete(options);    
        }
    }

    @Override
    public void deleteOption(String healthDataCode, Option option) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthDataCode(healthDataCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            options.getOptions().remove(option.name());
            mapper.save(options);
        }
    }

    @Override
    public Map<Option,String> getAllParticipantOptions(String healthDataCode) {
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthDataCode(healthDataCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        Map<Option,String> map = Maps.newHashMap();
        for (Option opt : Option.values()) {
            String value = (options == null) ? null : options.getOptions().get(opt.name());
            map.put(opt, value);
        }
        return map;
    }
    
    @Override
    public Map<String,String> getOptionForAllStudyParticipants(Study study, Option option) {
        // The only place we need the study, and that's to find all the options for all the 
        // participants in a given study.
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(study.getKey()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        List<FailedBatch> failures = mapper.batchDelete(mappings);
        BridgeUtils.ifFailuresThrowException(failures);
        
        Map<String,String> map = Maps.newHashMap();
        for (DynamoParticipantOptions opt : mappings) {
            map.put(opt.getHealthDataCode(), opt.getOptions().get(option.name()));
        }
        return map;
    }

}
