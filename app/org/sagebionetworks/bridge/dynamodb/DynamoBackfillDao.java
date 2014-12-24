package org.sagebionetworks.bridge.dynamodb;

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

public class DynamoBackfillDao implements BackfillDao {

    private DynamoDBMapper taskMapper;
    private DynamoDBMapper recordMapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.EVENTUAL)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoBackfillTask.class)).build();
        taskMapper = new DynamoDBMapper(client, mapperConfig);
        mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.EVENTUAL)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoBackfillRecord.class)).build();
        recordMapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public BackfillTask createTask(String user, String name) {
        DynamoBackfillTask task = new DynamoBackfillTask(user, name);
        taskMapper.save(task);
        return task;
    }

    @Override
    public BackfillRecord createRecord(String taskId, String studyId, String accountId, String operation) {
        DynamoBackfillRecord record = new DynamoBackfillRecord(taskId, studyId, accountId, operation);
        recordMapper.save(record);
        return record;
    }

    @Override
    public void updateTaskStatus(String staskId, BackfillStatus status) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public BackfillTask getTask(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<BackfillTask> getTasks(String name, long since) {
        // TODO Auto-generated method stub
        return null;
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
