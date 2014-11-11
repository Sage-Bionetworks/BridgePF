package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.events.SchedulePlanCreatedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanDeletedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanUpdatedEvent;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoSchedulePlanDao implements SchedulePlanDao, ApplicationEventPublisherAware {

    private DynamoDBMapper mapper;
    private ApplicationEventPublisher publisher;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSchedulePlan.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    
    @Override
    public List<SchedulePlan> getSchedulePlans(Study study) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(study.getKey()), "Study key is null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getKey());
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        
        return new ArrayList<SchedulePlan>(mapper.queryPage(DynamoSchedulePlan.class, query).getResults());
    }
    
    @Override
    public SchedulePlan getSchedulePlan(Study study, String guid) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(study.getKey()), "Study key is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(study.getKey());
        
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
        publisher.publishEvent(new SchedulePlanCreatedEvent(plan));
        return plan;
    }

    @Override
    public SchedulePlan updateSchedulePlan(SchedulePlan plan) {
        checkNotNull(plan, "Schedule plan is null");
        
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        mapper.save(plan);
        publisher.publishEvent(new SchedulePlanUpdatedEvent(plan));
        return plan;
    }

    @Override
    public void deleteSchedulePlan(Study study, String guid) {
        checkNotNull(study, "Study is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        SchedulePlan plan = getSchedulePlan(study, guid);
        mapper.delete(plan);
        publisher.publishEvent(new SchedulePlanDeletedEvent(plan));
    }

}
