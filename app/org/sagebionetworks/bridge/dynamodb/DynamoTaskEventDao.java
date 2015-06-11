package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;

@Component
public class DynamoTaskEventDao implements TaskEventDao {

    private static final String ANSWERED_EVENT_POSTFIX = ":"+TaskEventType.ANSWERED.name().toLowerCase();
    
    private DynamoDBMapper mapper;

    @Resource(name = "taskEventDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public void publishEvent(TaskEvent event) {
        checkNotNull(event);
        
        DynamoTaskEvent hashKey = new DynamoTaskEvent();
        hashKey.setHealthCode(event.getHealthCode());
        hashKey.setEventId(event.getEventId());
        
        // Only save if the timestamp is later than the current timestamp in the table
        TaskEvent savedEvent = mapper.load(hashKey);
        if (savedEvent == null || event.getTimestamp() > savedEvent.getTimestamp()) {
            mapper.save(event);    
        }
    }

    @Override
    public Map<String, DateTime> getTaskEventMap(String healthCode) {
        checkNotNull(healthCode);
        
        DynamoTaskEvent hashKey = new DynamoTaskEvent();
        hashKey.setHealthCode(healthCode);
        DynamoDBQueryExpression<DynamoTaskEvent> query = new DynamoDBQueryExpression<DynamoTaskEvent>()
            .withHashKeyValues(hashKey);

        PaginatedQueryList<DynamoTaskEvent> queryResults = mapper.query(DynamoTaskEvent.class, query);
        
        Builder<String,DateTime> builder = ImmutableMap.<String,DateTime>builder();
        for (DynamoTaskEvent event : queryResults) {
            builder.put(getEventMapKey(event), new DateTime(event.getTimestamp()));
        }
        return builder.build();
    }
    
    @Override
    public void deleteTaskEvents(String healthCode) {
        checkNotNull(healthCode);
        
        DynamoTaskEvent hashKey = new DynamoTaskEvent();
        hashKey.setHealthCode(healthCode);
        DynamoDBQueryExpression<DynamoTaskEvent> query = new DynamoDBQueryExpression<DynamoTaskEvent>()
            .withHashKeyValues(hashKey);

        PaginatedQueryList<DynamoTaskEvent> queryResults = mapper.query(DynamoTaskEvent.class, query);
        
        List<DynamoTaskEvent> objectsToDelete = Lists.newArrayList();
        objectsToDelete.addAll(queryResults);
        
        if (!objectsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(objectsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    /**
     * Answer events do schedule against a specific answer, which is added to the key in the
     * map only. A change in the value is continued to be a change to the same event.
     * @param event
     * @return
     */
    private String getEventMapKey(DynamoTaskEvent event) {
        if (event.getEventId().endsWith(ANSWERED_EVENT_POSTFIX)) {
            return event.getEventId()+"="+event.getAnswerValue();
        }
        return event.getEventId();    
    }
    
}
