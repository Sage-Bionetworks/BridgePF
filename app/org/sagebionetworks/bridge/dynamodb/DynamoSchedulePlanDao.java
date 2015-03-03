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
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

@Component
public class DynamoSchedulePlanDao implements SchedulePlanDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSchedulePlan.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "studyIdentifier is null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(studyIdentifier.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        
        return new ArrayList<SchedulePlan>(mapper.queryPage(DynamoSchedulePlan.class, query).getResults());
    }
    
    @Override
    public SchedulePlan getSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, "studyIdentifier is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(studyIdentifier.getIdentifier());
        
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
    public void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, "Study identifier is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        SchedulePlan plan = getSchedulePlan(studyIdentifier, guid);
        mapper.delete(plan);
    }
    
    @Override
    public List<SchedulePlan> getSchedulePlansForSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        List<SchedulePlan> results = Lists.newArrayList();
        
        for (SchedulePlan plan : getSchedulePlans(studyIdentifier)) {
            if (plan.getStrategy().doesScheduleSurvey(keys)) {
                results.add(plan);
            }
        }
        return results;
    }

}
