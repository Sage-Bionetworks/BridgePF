package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.createReferentGuidIndex;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
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
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoScheduledActivityDao implements ScheduledActivityDao {

    private static final String HEALTH_CODE = "healthCode";

    private static final String GUID = "guid";
    
    private static final String REFERENT_GUID = "referentGuid";

    private static final String INVALID_KEY_MSG = "Invalid offsetKey (may exceed maximum seek for value range): ";
    
    private DynamoDBMapper mapper;
    
    private DynamoIndexHelper referentIndex;
    
    @Resource(name = "activityDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "healthCodeReferentGuidIndex")
    final void setReferentIndex(DynamoIndexHelper index) {
        this.referentIndex = index;
    }
    
    @Override
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistoryV2(String healthCode,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize) {
        checkNotNull(healthCode);
        checkNotNull(scheduledOnStart);
        checkNotNull(scheduledOnEnd);
        checkNotNull(activityGuid);
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
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
        
        if (offsetKey != null) {
            Map<String,AttributeValue> map = Maps.newHashMap();
            map.put(HEALTH_CODE, new AttributeValue(healthCode));
            map.put(GUID, new AttributeValue(activityGuid+":"+offsetKey));
            
            query.withExclusiveStartKey(map);
        }
        
        QueryResultPage<DynamoScheduledActivity> queryResult = mapper.queryPage(DynamoScheduledActivity.class, query);

        List<ScheduledActivity> activities = Lists.newArrayListWithCapacity(queryResult.getResults().size());
        for (DynamoScheduledActivity act : queryResult.getResults()) {
            act.setTimeZone(scheduledOnStart.getZone());
            activities.add((ScheduledActivity)act);
        }
        
        String nextPageOffsetKey = null;
        if (queryResult.getLastEvaluatedKey() != null) {
            nextPageOffsetKey = queryResult.getLastEvaluatedKey().get(GUID).getS().split(":",2)[1];
        }

        return new ForwardCursorPagedResourceList<ScheduledActivity>(activities, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.SCHEDULED_ON_START, scheduledOnStart)
                .withRequestParam(ResourceList.SCHEDULED_ON_END, scheduledOnEnd);
    }
    
    /** {@inheritDoc} */
    @Override
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistoryV3(final String healthCode,
            final ActivityType activityType, final String referentGuid, final DateTime scheduledOnStart,
            final DateTime scheduledOnEnd, final String offsetKey, final int pageSize) {
        checkNotNull(healthCode);
        checkNotNull(activityType);
        checkNotNull(referentGuid);
        checkNotNull(scheduledOnStart);
        checkNotNull(scheduledOnEnd);

        int pageSizeWithIndicator = pageSize+1;
        String start = createReferentGuidIndex(activityType, referentGuid, scheduledOnStart.toLocalDateTime().toString());
        String end = createReferentGuidIndex(activityType, referentGuid, scheduledOnEnd.toLocalDateTime().toString());
        
        // GSIs don't support offset keys, but we can simulate by setting lower-bound range key to the page boundary
        if (offsetKey != null) {
            String[] components = offsetKey.split(":",2);
            start = createReferentGuidIndex(activityType, referentGuid, components[1]);
        }
        RangeKeyCondition dateCondition = new RangeKeyCondition(REFERENT_GUID).between(start, end);
        
        List<DynamoScheduledActivity> itemsToLoad = query(healthCode, offsetKey, pageSizeWithIndicator,
                dateCondition);
        
        // If there's an offsetKey, we need to verify it exists in the page set. In rare conditions where multiple schedules 
        // schedule the same referent at the same time, the referentGuids will be identical and in theory, could even span 
        // pages. We expand the record set to a point where the client would have to schedule the exact same task at the 
        // exact same time many dozens of times before we would not be able to page correctly... at which point the client 
        // has other issues.
        if (offsetKey != null) {
            if (!containsIndicatorRecord(itemsToLoad, pageSizeWithIndicator, offsetKey)) {
                int largerPageSize = pageSizeWithIndicator + API_MAXIMUM_PAGE_SIZE;
                itemsToLoad = query(healthCode, offsetKey, largerPageSize, dateCondition);
            }
            if (indexOfIndicator(itemsToLoad, offsetKey) == -1) {
                throw new BadRequestException(INVALID_KEY_MSG + offsetKey);
            }
        }
        
        // Truncate from index of indicator record, to pageSizeWithIndicator number of records
        itemsToLoad = truncateListToStartAtIndicatorRecord(itemsToLoad, offsetKey);
        itemsToLoad = itemsToLoad.subList(0, Math.min(itemsToLoad.size(), pageSizeWithIndicator));
        
        // Load the full items
        Map<String, List<Object>> resultMap = mapper.batchLoad(itemsToLoad);
        List<ScheduledActivity> results = Lists.newArrayListWithCapacity(itemsToLoad.size());
        for (List<Object> list : resultMap.values()) {
            for (Object oneResult : list) {
                ScheduledActivity activity = (ScheduledActivity)oneResult;
                activity.setTimeZone(scheduledOnStart.getZone());
                results.add(activity);
            }
        }
        results.sort(ScheduledActivity::compareByReferentGuidThenGuid);
        
        // determine the offset key
        String nextPageOffsetKey = null;
        if (results.size() == pageSizeWithIndicator) {
            nextPageOffsetKey = Iterables.getLast(results).getGuid();
        }
        
        results = results.subList(0, Math.min(results.size(),pageSize));
        return new ForwardCursorPagedResourceList<ScheduledActivity>(results, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.SCHEDULED_ON_START, scheduledOnStart)
                .withRequestParam(ResourceList.SCHEDULED_ON_END, scheduledOnEnd);
    }
    
    private List<DynamoScheduledActivity> query(String healthCode, String offsetKey, int querySize,
            RangeKeyCondition dateCondition) {
        
        QuerySpec spec = new QuerySpec()
                .withScanIndexForward(true)
                .withHashKey(HEALTH_CODE, healthCode)
                .withMaxPageSize(querySize)
                .withRangeKeyCondition(dateCondition);
        
        QueryOutcome outcome = referentIndex.query(spec);
        
        List<DynamoScheduledActivity> itemsToLoad = Lists.newArrayList();
        for (Item item : outcome.getItems()) {
            DynamoScheduledActivity keys = new DynamoScheduledActivity();
            keys.setGuid(item.getString(GUID));
            keys.setReferentGuid(item.getString(REFERENT_GUID));
            keys.setHealthCode(item.getString(HEALTH_CODE));
            itemsToLoad.add(keys);
        }
        itemsToLoad.sort(ScheduledActivity::compareByReferentGuidThenGuid);
        return itemsToLoad;
    }
    
    /**
     * Verify that the indicator record is in the list AND there are enough records to fulfill a page 
     * (list size, minus the index of the indicator record, is still >= the desired pageSizeWithIndicator).
     */
    private boolean containsIndicatorRecord(List<? extends ScheduledActivity> activities, int pageSizeWithIndicator, String guid) {
        int i = indexOfIndicator(activities, guid);
        return i != -1 && ((activities.size()-i) >= pageSizeWithIndicator);
    }
    
    private List<DynamoScheduledActivity> truncateListToStartAtIndicatorRecord(List<DynamoScheduledActivity> activities, String guid) {
        int indexOfIndicator = indexOfIndicator(activities, guid);
        if (indexOfIndicator == -1) {
            return activities;
        }
        return activities.subList(indexOfIndicator, activities.size());
    }
    
    private int indexOfIndicator(List<? extends ScheduledActivity> activities, String guid) {
        if (guid != null) {
            for (int i=0; i < activities.size(); i++) {
                if (activities.get(i).getGuid().equals(guid)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /** {@inheritDoc} */
    @Override
    public ScheduledActivity getActivity(DateTimeZone timeZone, String healthCode, String guid, boolean throwException) {
        checkNotNull(timeZone);
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        DynamoScheduledActivity hashKey = new DynamoScheduledActivity();
        hashKey.setHealthCode(healthCode);
        hashKey.setGuid(guid);
        
        ScheduledActivity dbActivity = mapper.load(hashKey);
        if (throwException && dbActivity == null) {
            throw new EntityNotFoundException(ScheduledActivity.class);
        }
        if (dbActivity != null) {
            dbActivity.setTimeZone(timeZone);    
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
        
        Map<String,AttributeValue> lastKey = null;
        do {
            DynamoDBQueryExpression<DynamoScheduledActivity> query = new DynamoDBQueryExpression<DynamoScheduledActivity>()
                    .withExclusiveStartKey(lastKey)
                    .withHashKeyValues(hashKey);
            
            QueryResultPage<DynamoScheduledActivity> queryResults = mapper.queryPage(DynamoScheduledActivity.class, query);
            List<DynamoScheduledActivity> activities = queryResults.getResults();
            if (!activities.isEmpty()) {
                List<FailedBatch> failures = mapper.batchDelete(activities);
                BridgeUtils.ifFailuresThrowException(failures);
            }
            lastKey = queryResults.getLastEvaluatedKey();
        } while(lastKey != null);        
    }
    
}
