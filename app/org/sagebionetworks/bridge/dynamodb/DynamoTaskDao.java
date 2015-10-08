package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;
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
public class DynamoTaskDao implements TaskDao {
    
    private DynamoDBMapper mapper;
    private DynamoIndexHelper index;
    
    @Resource(name = "taskDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "taskIndex")
    public void setTaskIndex(DynamoIndexHelper index) {
        this.index = index;
    }
    
    /** {@inheritDoc} */
    @Override
    public List<Task> getTasks(ScheduleContext context) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(context.getHealthCode());

        // Exclude everything hidden before *now*
        AttributeValue attribute = new AttributeValue().withN(Long.toString(context.getNow().getMillis()));
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.GT)
            .withAttributeValueList(attribute);

        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withQueryFilterEntry("hidesOn", condition)
            .withHashKeyValues(hashKey);

        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        List<Task> tasks = Lists.newArrayList();
        for (DynamoTask task : queryResults) {
            // Although we don't create tasks for applications that are of the wrong version for a schedule,
            // we do create tasks into the future, and that means tasks can get out of sync with the version 
            // of the app requesting tasks. We must filter here, as well as when we retrieve schedule plans for 
            // scheduling.
            if (context.getClientInfo().isTargetedAppVersion(task.getMinAppVersion(), task.getMaxAppVersion())) {
                task.setTimeZone(context.getZone());
                tasks.add(task);
            }
        }
        Collections.sort(tasks, Task.TASK_COMPARATOR);
        return tasks;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean taskRunHasNotOccurred(String healthCode, String runKey) {
        RangeKeyCondition rangeKeyCondition = new RangeKeyCondition("runKey").eq(runKey);
        int count = index.queryKeyCount("healthCode", healthCode, rangeKeyCondition);
        return (count == 0);
    }
    
    /** {@inheritDoc} */
    @Override
    public void saveTasks(List<Task> tasks) {
        if (!tasks.isEmpty()) {
            // Health code is (now) set during construction in the scheduler.
            List<FailedBatch> failures = mapper.batchSave(tasks);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void updateTasks(String healthCode, List<Task> tasks) {
        List<Task> tasksToSave = Lists.newArrayList();
        for (Task task : tasks) {
            if (task != null && (task.getStartedOn() != null || task.getFinishedOn() != null)) {
                DynamoTask hashKey = new DynamoTask();
                hashKey.setHealthCode(healthCode);
                hashKey.setGuid(task.getGuid());
                DynamoTask dbTask = mapper.load(hashKey);
                
                if (dbTask != null) {
                    if (task.getStartedOn() != null) {
                        dbTask.setStartedOn(task.getStartedOn());
                        dbTask.setHidesOn(new Long(Long.MAX_VALUE));
                    }
                    if (task.getFinishedOn() != null) {
                        dbTask.setFinishedOn(task.getFinishedOn());
                        dbTask.setHidesOn(task.getFinishedOn());
                    }
                    tasksToSave.add(dbTask);
                }
            }
        }
        if (!tasksToSave.isEmpty()) {
            List<FailedBatch> failures = mapper.batchSave(tasksToSave);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void deleteTasks(String healthCode) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>().withHashKeyValues(hashKey);
        
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        // Confirmed that you have to transfer these tasks to a list or the batchDelete does not work. 
        List<DynamoTask> tasksToDelete = Lists.newArrayListWithCapacity(queryResults.size());
        tasksToDelete.addAll(queryResults);
        
        if (!tasksToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(tasksToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

}
