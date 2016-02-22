package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

@Component
public class DynamoSchedulePlanDao implements SchedulePlanDao {

    private DynamoDBMapper mapper;
    private CriteriaDao criteriaDao;

    @Resource(name = "schedulePlanMapper")
    final void setSchedulePlanMapper(DynamoDBMapper schedulePlanMapper) {
        this.mapper = schedulePlanMapper;
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, StudyIdentifier studyIdentifier) {
        checkNotNull(clientInfo);
        checkNotNull(studyIdentifier);
        
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
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(guid));
        
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
        checkNotNull(studyIdentifier);
        checkNotNull(plan);
        
        plan.setStudyKey(studyIdentifier.getIdentifier());
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        forEachCriteria(plan, scheduleCriteria -> persistCriteria(scheduleCriteria));
        mapper.save(plan);
        return plan;
    }

    @Override
    public SchedulePlan updateSchedulePlan(StudyIdentifier studyIdentifier, SchedulePlan plan) {
        checkNotNull(studyIdentifier);
        checkNotNull(plan);
        
        plan.setStudyKey(studyIdentifier.getIdentifier());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        forEachCriteria(plan, scheduleCriteria -> persistCriteria(scheduleCriteria));
        mapper.save(plan);
        return plan;
    }

    @Override
    public void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(guid));
        
        SchedulePlan plan = getSchedulePlan(studyIdentifier, guid);
        
        forEachCriteria(plan, scheduleCriteria -> deleteCriteria(scheduleCriteria));
        mapper.delete(plan);
    }
    
    private void forEachCriteria(SchedulePlan plan, Function<ScheduleCriteria,Criteria> consumer) {
        if (plan.getStrategy() instanceof CriteriaScheduleStrategy) {
            CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
            for (int i=0; i < strategy.getScheduleCriteria().size(); i++) {
                ScheduleCriteria scheduleCriteria = strategy.getScheduleCriteria().get(i);
                
                // Add the key in order to load
                scheduleCriteria.getCriteria().setKey(getKey(plan, i));

                Criteria criteria = consumer.apply(scheduleCriteria);
                
                // Update the criteria object (except for delete). This may add a new criteria object, 
                // which will return the plan to the caller with the criteria stubbed out in the JSON.
                if (criteria != null) {
                    scheduleCriteria = new ScheduleCriteria(scheduleCriteria.getSchedule(), criteria);
                    strategy.getScheduleCriteria().set(i, scheduleCriteria);
                }
            }
        }        
    }
    
    private String getKey(SchedulePlan plan, int index) {
        return "scheduleCriteria:" + plan.getGuid() + ":" + index;
    }
    
    /**
     * Save the criteria object if it exists. If not, return an empty criteria object which will return the 
     * criteria stubbed out in the JSON representation of the schedule plan.
     */
    private Criteria persistCriteria(ScheduleCriteria scheduleCriteria) {
        Criteria criteria = scheduleCriteria.getCriteria();
        if (criteria != null) {
            criteriaDao.createOrUpdateCriteria(criteria);
        }
        return (criteria != null) ? criteria : Criteria.create();
    }

    /**
     * Load criteria. If the criteria object doesn't exist, create and return one as part of the schedule plan.
     */
    private Criteria loadCriteria(ScheduleCriteria scheduleCriteria) {
        String key = scheduleCriteria.getCriteria().getKey();
        Criteria criteria = criteriaDao.getCriteria(key);
        if (criteria == null) {
            criteria = Criteria.create();
            criteria.setKey(key);
        }
        return criteria;
    }
    
    private Criteria deleteCriteria(ScheduleCriteria scheduleCriteria) {
        criteriaDao.deleteCriteria(scheduleCriteria.getCriteria().getKey());
        return null;
    }

}
