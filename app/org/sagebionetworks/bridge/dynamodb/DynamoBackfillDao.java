package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoBackfillDao implements BackfillDao {

    private DynamoDBMapper taskMapper;
    private DynamoDBMapper recordMapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoBackfillTask.class)).build();
        taskMapper = new DynamoDBMapper(client, mapperConfig);
        mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.EVENTUAL)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoBackfillRecord.class)).build();
        recordMapper = new DynamoDBMapper(client, mapperConfig);
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
    public List<? extends BackfillTask> getTasks(String taskName, long since) {
        DynamoBackfillTask hashKey = new DynamoBackfillTask();
        hashKey.setName(taskName);
        Condition rangeKeyCondition = new Condition().withAttributeValueList(
                new AttributeValue().withN(Long.toString(since)));
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
    public List<BackfillRecord> getRecords(String taskId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getRecordCount(String taskId) {
        // TODO Auto-generated method stub
        return 0;
    }
}
