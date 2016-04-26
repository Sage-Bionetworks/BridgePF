package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Iterator;
import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.models.backfill.BackfillRecord;
import org.sagebionetworks.bridge.models.backfill.BackfillStatus;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoBackfillDao implements BackfillDao {

    private DynamoDBMapper taskMapper;
    private DynamoDBMapper recordMapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper) {
        DynamoDBMapperConfig taskMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoBackfillTask.class)).build();
        taskMapper = new DynamoDBMapper(client, taskMapperConfig);
        DynamoDBMapperConfig recordMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoBackfillRecord.class)).build();
        recordMapper = new DynamoDBMapper(client, recordMapperConfig);
    }

    @Override
    public BackfillTask createTask(String name, String user) {
        checkArgument(isNotBlank(name));
        checkArgument(isNotBlank(user));
        DynamoBackfillTask task = new DynamoBackfillTask(name, user);
        taskMapper.save(task);
        return task;
    }

    @Override
    public void updateTaskStatus(String taskId, BackfillStatus status) {
        checkArgument(isNotBlank(taskId));
        checkNotNull(status);
        DynamoBackfillTask task = new DynamoBackfillTask(taskId);
        task = taskMapper.load(task);
        task.setStatus(status.name());
        taskMapper.save(task);
    }

    @Override
    public BackfillTask getTask(String taskId) {
        checkArgument(isNotBlank(taskId));
        DynamoBackfillTask task = new DynamoBackfillTask(taskId);
        task = taskMapper.load(task);
        return task;
    }

    @Override
    public List<DynamoBackfillTask> getTasks(String taskName, long since) {
        DynamoBackfillTask hashKey = new DynamoBackfillTask();
        hashKey.setName(taskName);
        Condition rangeKeyCondition = new Condition().withAttributeValueList(
                new AttributeValue().withN(Long.toString(since))).withComparisonOperator(ComparisonOperator.GE);
        DynamoDBQueryExpression<DynamoBackfillTask> queryExpression = new DynamoDBQueryExpression<DynamoBackfillTask>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("timestamp", rangeKeyCondition);
        PaginatedQueryList<DynamoBackfillTask> results = taskMapper.query(DynamoBackfillTask.class, queryExpression);
        return results.subList(0, results.size());
    }

    @Override
    public BackfillRecord createRecord(String taskId, String studyId, String accountId, String operation) {
        checkArgument(isNotBlank(taskId));
        checkArgument(isNotBlank(studyId));
        checkArgument(isNotBlank(accountId));
        checkArgument(isNotBlank(operation));
        DynamoBackfillRecord record = new DynamoBackfillRecord(taskId, studyId, accountId, operation);
        recordMapper.save(record);
        return record;
    }

    @Override
    public Iterator<DynamoBackfillRecord> getRecords(String taskId) {
        DynamoDBQueryExpression<DynamoBackfillRecord> queryExpression = getRecordQueryExpression(taskId, 0);
        PaginatedQueryList<DynamoBackfillRecord> results = recordMapper.query(DynamoBackfillRecord.class, queryExpression);
        return results.iterator();
    }

    @Override
    public int getRecordCount(String taskId) {
        DynamoDBQueryExpression<DynamoBackfillRecord> queryExpression = getRecordQueryExpression(taskId, 0);
        int count = recordMapper.count(DynamoBackfillRecord.class, queryExpression);
        return count;
    }

    private DynamoDBQueryExpression<DynamoBackfillRecord> getRecordQueryExpression(String taskId, long since) {
        final DynamoBackfillRecord hashKey = new DynamoBackfillRecord();
        hashKey.setTaskId(taskId);
        final Condition rangeKeyCondition = new Condition()
                .withAttributeValueList(new AttributeValue().withN(Long.toString(since)))
                .withComparisonOperator(ComparisonOperator.GE);
        final DynamoDBQueryExpression<DynamoBackfillRecord> queryExpression = new DynamoDBQueryExpression<DynamoBackfillRecord>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("timestamp", rangeKeyCondition);
        return queryExpression;
    }
}
