package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoScheduledActivityDao implements ScheduledActivityDao {
    
    private static final String SCHEDULED_ON_START = "scheduledOnStart";

    private static final String SCHEDULED_ON_END = "scheduledOnEnd";

    private static final String HEALTH_CODE = "healthCode";

    private static final String GUID = "guid";

    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "activityDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistoryV2(String healthCode,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetBy,
            int pageSize) {
        checkNotNull(healthCode);
        checkNotNull(scheduledOnStart);
        checkNotNull(scheduledOnEnd);
        checkNotNull(activityGuid);
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(healthCode);
        
        String start = activityGuid + ":" + scheduledOnStart.toLocalDateTime().toString();
        String end = activityGuid + ":" + scheduledOnEnd.toLocalDateTime().toString();
        
        // range key is between start date and end date
        Condition dateCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withS(start),
                        new AttributeValue().withS(end));

        DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>()
            .withHashKeyValues(hashKey)
            .withLimit(pageSize)
            .withScanIndexForward(false)
            .withRangeKeyCondition(GUID, dateCondition);
        
        if (offsetBy != null) {
            Map<String,AttributeValue> map = Maps.newHashMap();
            map.put(HEALTH_CODE, new AttributeValue(healthCode));
            map.put(GUID, new AttributeValue(activityGuid+":"+offsetBy));
            
            query.withExclusiveStartKey(map);
        }
        
        QueryResultPage<DynamoScheduledActivity> queryResult = mapper.queryPage(DynamoScheduledActivity.class, query);

        List<ScheduledActivity> activities = Lists.newArrayListWithCapacity(queryResult.getResults().size());
        for (DynamoScheduledActivity act : queryResult.getResults()) {
            act.setTimeZone(DateTimeZone.UTC);
            activities.add((ScheduledActivity)act);
        }
        
        String nextOffsetBy = null;
        if (queryResult.getLastEvaluatedKey() != null) {
            nextOffsetBy = queryResult.getLastEvaluatedKey().get(GUID).getS().split(":",2)[1];
        }
        
        return new ForwardCursorPagedResourceList<ScheduledActivity>(activities, nextOffsetBy, pageSize)
                .withFilter(SCHEDULED_ON_START, scheduledOnStart)
                .withFilter(SCHEDULED_ON_END, scheduledOnEnd);
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

        DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>()
                .withHashKeyValues(hashKey);
        
        PaginatedQueryList<DynamoScheduledActivity> queryResults = mapper.query(DynamoScheduledActivity.class, query);
        
        // Confirmed that you have to transfer these activities to a list or the batchDelete does not work.
        List<ScheduledActivity> activitiesToDelete = Lists.newArrayListWithCapacity(queryResults.size());
        activitiesToDelete.addAll(queryResults);

        if (!activitiesToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(activitiesToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
}
