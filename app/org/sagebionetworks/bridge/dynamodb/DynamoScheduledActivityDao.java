package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@Component
public class DynamoScheduledActivityDao implements ScheduledActivityDao {
    
    private DynamoDBMapper mapper;
    private DynamoIndexHelper schedulePlanIndex;
    
    @Resource(name = "activityDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
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
    public List<ScheduledActivity> getActivities(DateTimeZone timeZone, List<ScheduledActivity> activities) {
        if (activities.isEmpty()) {
            return ImmutableList.of();
        }
        List<Object> activitiesToLoad = new ArrayList<Object>(activities);
        Map<String,List<Object>> resultMap = mapper.batchLoad(activitiesToLoad);
        
        // there's only one table of results returned.
        List<Object> activitiesLoaded = Iterables.getFirst(resultMap.values(), ImmutableList.of()); 
        
        List<ScheduledActivity> results = Lists.newArrayListWithCapacity(activitiesLoaded.size());
        for (Object object : activitiesLoaded) {
            ScheduledActivity activity = (ScheduledActivity)object;
            activity.setTimeZone(timeZone);
            results.add(activity);
        }
        Collections.sort(results, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return results;
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
        List<ScheduledActivity> activitiesToDelete = Lists.newArrayListWithCapacity(queryResults.size());
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
