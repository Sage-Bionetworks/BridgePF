package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
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

    private DynamoDBMapper mapper;

    @Resource(name = "taskEventDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public void publishEvent(TaskEvent event) {
        checkNotNull(event);
        
        // This is weird.
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
            builder.put(event.getEventId(), new DateTime(event.getTimestamp()));
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

}
