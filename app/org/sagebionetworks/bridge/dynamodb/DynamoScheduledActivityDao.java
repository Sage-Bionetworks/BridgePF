package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

@Component
public class DynamoScheduledActivityDao implements ScheduledActivityDao {
    
    private DynamoDBMapper mapper;
    private DynamoIndexHelper runKeyIndex;
    private DynamoIndexHelper schedulePlanIndex;
    
    @Resource(name = "activityDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "activityRunKeyIndex")
    public final void setActivityRunKeyIndex(DynamoIndexHelper index) {
        this.runKeyIndex = index;
    }
    
    @Resource(name = "activitySchedulePlanGuidIndex")
    public final void setActivitySchedulePlanGuidIndex(DynamoIndexHelper index) {
        this.schedulePlanIndex = index;
    }

    /** {@inheritDoc} */
    @Override
    public ScheduledActivity getActivity(String healthCode, String guid) {
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(healthCode);
        hashKey.setGuid(guid);
        
        ScheduledActivity dbActivity = mapper.load(hashKey);
        if (dbActivity == null) {
            throw new EntityNotFoundException(ScheduledActivity.class);
        }
        return dbActivity;
    }
    
    /** {@inheritDoc} */
    @Override
    public List<ScheduledActivity> getActivities(ScheduleContext context) {
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(context.getHealthCode());

        // Exclude everything hidden before *now*
        AttributeValue attribute = new AttributeValue().withN(Long.toString(context.getNow().getMillis()));
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.GT)
            .withAttributeValueList(attribute);

        DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>()
            .withQueryFilterEntry("hidesOn", condition)
            .withHashKeyValues(hashKey);

        PaginatedQueryList<DynamoScheduledActivity> queryResults = mapper.query(DynamoScheduledActivity.class, query);
        
        List<ScheduledActivity> activities = Lists.newArrayList();
        for (DynamoScheduledActivity activity : queryResults) {
            // Although we don't create activities for applications that are of the wrong version for a schedule,
            // we do create activities into the future, and that means activities can get out of sync with the version 
            // of the app requesting activities. We must filter here, as well as when we retrieve schedule plans for 
            // scheduling.
            if (context.getClientInfo().isTargetedAppVersion(activity.getMinAppVersion(), activity.getMaxAppVersion())) {
                activity.setTimeZone(context.getZone());
                activities.add(activity);
            }
        }
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return activities;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean activityRunHasNotOccurred(String healthCode, String runKey) {
        RangeKeyCondition rangeKeyCondition = new RangeKeyCondition("runKey").eq(runKey);
        int count = runKeyIndex.queryKeyCount("healthCode", healthCode, rangeKeyCondition);
        return (count == 0);
    }
    
    /** {@inheritDoc} */
    @Override
    public void saveActivities(List<ScheduledActivity> activities) {
        if (!activities.isEmpty()) {
            // Health code is (now) set during construction in the scheduler.
            List<FailedBatch> failures = mapper.batchSave(activities);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void updateActivities(String healthCode, List<ScheduledActivity> activities) {
        if (!activities.isEmpty()) {
            List<FailedBatch> failures = mapper.batchSave(activities);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void deleteActivitiesForUser(String healthCode) {
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>().withHashKeyValues(hashKey);
        
        PaginatedQueryList<DynamoScheduledActivity> queryResults = mapper.query(DynamoScheduledActivity.class, query);
        
        // Confirmed that you have to transfer these activities to a list or the batchDelete does not work. 
        List<DynamoScheduledActivity> activitiesToDelete = Lists.newArrayListWithCapacity(queryResults.size());
        activitiesToDelete.addAll(queryResults);
        
        if (!activitiesToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(activitiesToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void deleteActivitiesForSchedulePlan(String schedulePlanGuid) {
        ScheduledActivity activity = new DynamoScheduledActivity();
        activity.setSchedulePlanGuid(schedulePlanGuid);

        List<ScheduledActivity> activitiesToDelete = schedulePlanIndex
                .query(DynamoScheduledActivity.class, "schedulePlanGuid", schedulePlanGuid, null)
                .stream()
                .filter(act -> ScheduledActivityStatus.DELETABLE_STATUSES.contains(act.getStatus()))
                .collect(Collectors.toList());
        
        if (!activitiesToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(activitiesToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

}
