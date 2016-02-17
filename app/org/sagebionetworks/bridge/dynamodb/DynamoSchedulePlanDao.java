package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
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
    private CriteriaDao criteriaDao;

    @Autowired
    final void setDynamoDbClient(BridgeConfig bridgeConfig, AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(DynamoUtils.getTableNameOverride(DynamoSchedulePlan.class, bridgeConfig)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, StudyIdentifier studyIdentifier) {
        checkNotNull(clientInfo, "clientInfo is null");
        checkNotNull(studyIdentifier, "studyIdentifier is null");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setStudyKey(studyIdentifier.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSchedulePlan> query = new DynamoDBQueryExpression<DynamoSchedulePlan>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(plan);
        
        List<DynamoSchedulePlan> dynamoPlans = mapper.queryPage(DynamoSchedulePlan.class, query).getResults();
        
        ArrayList<SchedulePlan> plans = Lists.newArrayListWithCapacity(dynamoPlans.size());
        for(DynamoSchedulePlan dynamoPlan : dynamoPlans) {
            // We will continue to filter app version based min/max values for a plan. This is in use in prod. 
            // But future filtering will be moved to the ContextScheduleStrategy encapsulated in a schedule plan.  
            if (clientInfo.isTargetedAppVersion(dynamoPlan.getMinAppVersion(), dynamoPlan.getMaxAppVersion())) {
                plans.add(dynamoPlan);
            }
            forEachCriteria(dynamoPlan, scheduleCriteria -> loadCriteria(scheduleCriteria));
        }
        return plans;
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
        
        plan = plans.get(0);
        forEachCriteria(plan, scheduleCriteria -> loadCriteria(scheduleCriteria));
        return plan;
    }

    @Override
    public SchedulePlan createSchedulePlan(StudyIdentifier studyIdentifier, SchedulePlan plan) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkNotNull(plan, "Schedule plan is null");
        
        plan.setStudyKey(studyIdentifier.getIdentifier());
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        forEachCriteria(plan, scheduleCriteria -> loadCriteria(scheduleCriteria));
        mapper.save(plan);
        return plan;
    }

    @Override
    public SchedulePlan updateSchedulePlan(StudyIdentifier studyIdentifier, SchedulePlan plan) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkNotNull(plan, "Schedule plan is null");
        
        plan.setStudyKey(studyIdentifier.getIdentifier());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        forEachCriteria(plan, scheduleCriteria -> persistCriteria(scheduleCriteria));
        mapper.save(plan);
        return plan;
    }

    @Override
    public void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, "Study identifier is null");
        checkArgument(StringUtils.isNotBlank(guid), "Plan GUID is blank or null");
        
        SchedulePlan plan = getSchedulePlan(studyIdentifier, guid);
        
        forEachCriteria(plan, scheduleCriteria -> deleteCriteria(scheduleCriteria));
        mapper.delete(plan);
    }
    
    private void forEachCriteria(SchedulePlan plan, Function<ScheduleCriteria,Criteria> consumer) {
        if (plan.getStrategy() instanceof CriteriaScheduleStrategy) {
            CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
            for (int i=0; i < strategy.getScheduleCriteria().size(); i++) {
                ScheduleCriteria scheduleCriteria = strategy.getScheduleCriteria().get(i);
                
                scheduleCriteria = new ScheduleCriteria.Builder()
                        .withSchedule(scheduleCriteria.getSchedule())
                        .withCriteria(scheduleCriteria.getCriteria())
                        .withKey(getKey(plan, i)).build();

                Criteria criteria = consumer.apply(scheduleCriteria);

                scheduleCriteria = new ScheduleCriteria.Builder()
                        .withSchedule(scheduleCriteria.getSchedule())
                        .withCriteria(criteria).build();
                strategy.getScheduleCriteria().set(i, scheduleCriteria);
            }
        }        
    }
    
    private String getKey(SchedulePlan plan, int index) {
        return plan.getGuid()+":scheduleCriteria:" + index;
    }
    
    private Criteria persistCriteria(ScheduleCriteria scheduleCriteria) {
        Criteria criteria = scheduleCriteria.getCriteria();
        Criteria makeCopyOf = (criteria == null) ? scheduleCriteria : criteria;

        Criteria copy = criteriaDao.copyCriteria(scheduleCriteria.getKey(), makeCopyOf);
        criteriaDao.createOrUpdateCriteria(copy);
        return copy;
    }

    private Criteria loadCriteria(ScheduleCriteria scheduleCriteria) {
        Criteria criteria = criteriaDao.getCriteria(scheduleCriteria.getKey());
        if (criteria == null) {
            criteria = criteriaDao.copyCriteria(scheduleCriteria.getKey(), scheduleCriteria);
        }
        return criteria;
    }
    
    private Criteria deleteCriteria(ScheduleCriteria scheduleCriteria) {
        criteriaDao.deleteCriteria(scheduleCriteria.getKey());
        return null;
    }

}
