package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

public class DynamoSchedulePlanDao implements SchedulePlanDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSchedulePlan.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(Study study) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(study.getIdentifier()), "Study key is null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        
        return new ArrayList<SchedulePlan>(mapper.queryPage(DynamoSchedulePlan.class, query).getResults());
    }
    
    @Override
    public SchedulePlan getSchedulePlan(Study study, String guid) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(study.getIdentifier()), "Study key is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getIdentifier());
        
        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(guid));
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        query.withRangeKeyCondition("guid", condition);
        
        List<DynamoSchedulePlan> plans = mapper.queryPage(DynamoSchedulePlan.class, query).getResults();
        if (plans.isEmpty()) {
            throw new EntityNotFoundException(SchedulePlan.class);
        }
        return plans.get(0);
    }

    @Override
    public SchedulePlan createSchedulePlan(SchedulePlan plan) {
        checkNotNull(plan, "Schedule plan is null");
        
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        mapper.save(plan);
        return plan;
    }

    @Override
    public SchedulePlan updateSchedulePlan(SchedulePlan plan) {
        checkNotNull(plan, "Schedule plan is null");
        
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        mapper.save(plan);
        return plan;
    }

    @Override
    public void deleteSchedulePlan(Study study, String guid) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        SchedulePlan plan = getSchedulePlan(study, guid);
        mapper.delete(plan);
    }
    
    @Override
    public List<SchedulePlan> getSchedulePlansForSurvey(Study study, GuidCreatedOnVersionHolder keys) {
        List<SchedulePlan> results = Lists.newArrayList();
        
        for (SchedulePlan plan : getSchedulePlans(study)) {
            if (plan.getStrategy().doesScheduleSurvey(keys)) {
                results.add(plan);
            }
        }
        return results;
    }

}
