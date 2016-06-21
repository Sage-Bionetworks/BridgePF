package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@Component
public class DynamoScheduledActivityDao implements ScheduledActivityDao {
    
    private static final String HEALTH_CODE = "healthCode";

    private static final String GUID = "guid";

    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "activityDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public PagedResourceList<? extends ScheduledActivity> getActivityHistory(String healthCode, String offsetKey, int pageSize){
        // Just set a sane upper limit on this.
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        QueryResultPage<DynamoScheduledActivity> page = mapper.queryPage(DynamoScheduledActivity.class,
                createGetQuery(healthCode, offsetKey, pageSize));
        
        String nextPageOffsetKey = (page.getLastEvaluatedKey() != null) ? page.getLastEvaluatedKey().get(GUID).getS() : null;
        
        int total = mapper.count(DynamoScheduledActivity.class, createCountQuery(healthCode));
        
        PagedResourceList<? extends ScheduledActivity> resourceList = new PagedResourceList<>(page.getResults(), null,
                pageSize, total).withOffsetKey(nextPageOffsetKey);
        
        for (ScheduledActivity activity : resourceList.getItems()) {
            activity.setTimeZone(DateTimeZone.UTC);
        }
        return resourceList;
    }

    /**
     * Get the count query (applies filters) and then sets an offset key and the limit to a page of records, 
     * plus one, to determine if there are records beyond the current page. 
     */
    private DynamoDBQueryExpression<DynamoScheduledActivity> createGetQuery(String healthCode, String offsetKey,
            int pageSize) {

        DynamoDBQueryExpression<DynamoScheduledActivity> query = createCountQuery(healthCode);
        if (offsetKey != null) {
            Map<String,AttributeValue> map = new HashMap<>();
            map.put(HEALTH_CODE, new AttributeValue().withS(healthCode));
            map.put(GUID, new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }
        query.withLimit(pageSize);
        return query;
    }

    /**
     * Create a query for records applying the filter values if they exist.
     */
    private DynamoDBQueryExpression<DynamoScheduledActivity> createCountQuery(String healthCode) {
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setHealthCode(healthCode);
        
        DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>();
        query.withHashKeyValues(schActivity);
        query.setScanIndexForward(false);
        return query;
    }
    
    
    /** {@inheritDoc} */
    @Override
    public ScheduledActivity getActivity(DateTimeZone timeZone, String healthCode, String guid) {
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(healthCode);
        hashKey.setGuid(guid);
        
        ScheduledActivity dbActivity = mapper.load(hashKey);
        if (dbActivity == null) {
            throw new EntityNotFoundException(ScheduledActivity.class);
        }
        dbActivity.setTimeZone(timeZone);
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
