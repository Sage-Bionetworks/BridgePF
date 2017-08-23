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
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
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

    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
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
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        
        int sizeWithIndicatorRecord = pageSize+1;
        String start = BridgeUtils.createReferentGuid(activityType, referentGuid, scheduledOnStart.toLocalDateTime().toString());
        String end = BridgeUtils.createReferentGuid(activityType, referentGuid, scheduledOnEnd.toLocalDateTime().toString());
        
        if (offsetKey != null){
            String[] components = offsetKey.split(":", 2);
            start = BridgeUtils.createReferentGuid(activityType, referentGuid, components[1]);
        }
        RangeKeyCondition dateCondition = new RangeKeyCondition(REFERENT_GUID).between(start, end);
        
        // Two schedules can generate the same referentGuids if they point to the same referent and schedule identical
        // scheduldOn times. This means that the offsetKey must be the GUID, while the rangeKey is the referentGuid. We
        // query with the rangeKey, increasing page size in the rare situation that a full page contains entirely items
        // with the same referentGuid. Then we index into those results with the offsetKey, truncate to page size, and
        // load these items fully from DynamoDB (with one more indicator record to determine the next page of records).
        List<DynamoScheduledActivity> itemsToLoad = queryToEndOfReferentGuidRun(healthCode, offsetKey,
                sizeWithIndicatorRecord, dateCondition);

        List<ScheduledActivity> results = Lists.newArrayListWithCapacity(itemsToLoad.size());
        Map<String, List<Object>> resultMap = mapper.batchLoad(itemsToLoad);
        for (List<Object> resultList : resultMap.values()) {
            for (Object oneResult : resultList) {
                ScheduledActivity activity = (ScheduledActivity)oneResult;
                activity.setTimeZone(scheduledOnStart.getZone());
                results.add(activity);
            }
        }
        results.sort(ScheduledActivity::compareByReferentGuidThenGuid);
        
        String nextPageOffsetKey = null;
        if (results.size() > pageSize) {
            nextPageOffsetKey = Iterables.getLast(results).getGuid();
        }

        int lastIndex = Math.min(pageSize, results.size());
        return new ForwardCursorPagedResourceList<ScheduledActivity>(results.subList(0, lastIndex), nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.SCHEDULED_ON_START, scheduledOnStart)
                .withRequestParam(ResourceList.SCHEDULED_ON_END, scheduledOnEnd);
    }

    private List<DynamoScheduledActivity> queryToEndOfReferentGuidRun(String healthCode, String offsetKey,
            int sizeWithIndicatorRecord, RangeKeyCondition dateCondition) {
        QuerySpec spec = new QuerySpec()
                .withScanIndexForward(true)
                .withHashKey(HEALTH_CODE, healthCode)
                .withMaxPageSize(sizeWithIndicatorRecord)
                .withRangeKeyCondition(dateCondition);
        
        QueryOutcome outcome = referentIndex.query(spec);
        
        List<DynamoScheduledActivity> itemsToLoad = Lists.newArrayList();
        for (Item item : outcome.getItems()) {
            // Presumably faster than using JSON conversion
            DynamoScheduledActivity indexKeys = new DynamoScheduledActivity();
            indexKeys.setGuid((String)item.asMap().get("guid"));
            indexKeys.setReferentGuid((String)item.asMap().get("referentGuid"));
            indexKeys.setHealthCode((String)item.asMap().get("healthCode"));
            itemsToLoad.add(indexKeys); 
        }
        
        if (!itemsToLoad.isEmpty()) {
            ScheduledActivity last = Iterables.getLast(itemsToLoad);
            if (itemsToLoad.get(0).getReferentGuid().equals(last.getReferentGuid())) {
                // Not good enough, query again with a larger page size. This should only happen
                // in the case of some spectacular referentGuid collisions, see test for this behavior.
                return queryToEndOfReferentGuidRun(healthCode, offsetKey,
                        Math.round(sizeWithIndicatorRecord * 1.4f), dateCondition);
            }
        }
        return seekToSublist(offsetKey, sizeWithIndicatorRecord, itemsToLoad);
    }
    
    private List<DynamoScheduledActivity> seekToSublist(final String offsetKey, int sizeWithIndicatorRecord,
            List<DynamoScheduledActivity> itemsToLoad) {

        int offsetIndex = 0;
        if (offsetKey != null) {
            for (int i=0; i < itemsToLoad.size(); i++) {
                if (itemsToLoad.get(i).getGuid().equals(offsetKey)) {
                    offsetIndex = i;
                    break;
                }
            }
        }
        int lastIndex = Math.min(offsetIndex+sizeWithIndicatorRecord, itemsToLoad.size());
        return itemsToLoad.subList(offsetIndex, lastIndex);
    }
    
    /*
     * Getting the key, get the last one, count its index position (0 to N), add to key
     * Retrieving, if there's an offset, add it to the page size, then index into the list by offset
     * 
     */
    
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
