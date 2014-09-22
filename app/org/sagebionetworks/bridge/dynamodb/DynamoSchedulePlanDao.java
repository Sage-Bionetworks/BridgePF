package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoSchedulePlanDao implements SchedulePlanDao {

    private static final SchedulePlanValidator VALIDATOR = new SchedulePlanValidator();
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSchedulePlan.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Override
    public List<? extends SchedulePlan> getSchedulePlans(Study study) {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getKey());
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        
        // TODO: This seems like a better way than how I cast this using surveys 
        // (maybe even looping through and downcasting or something crazy like that).
        return (List<? extends SchedulePlan>)mapper.queryPage(DynamoSchedulePlan.class, query).getResults();
    }
    
    @Override
    public SchedulePlan getSchedulePlan(Study study, String guid) {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getKey());
        
        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(guid));
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        query.withRangeKeyCondition("guid", condition);
        
        return mapper.queryPage(DynamoSchedulePlan.class, query).getResults().get(0);
    }

    @Override
    public GuidHolder addSchedulePlan(SchedulePlan plan) {
        plan.setGuid(BridgeUtils.generateGuid());
        VALIDATOR.validate(plan);
        mapper.save(plan);
        return new GuidHolder(plan.getGuid());
    }

    @Override
    public void updateSchedulePlan(SchedulePlan plan) {
        VALIDATOR.validate(plan);
        mapper.save(plan);
    }

    @Override
    public void deleteSchedulePlan(Study study, String guid) {
        SchedulePlan plan = getSchedulePlan(study, guid);
        mapper.delete(plan);
    }

}
