package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.dao.ScheduleDao;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;

public class DynamoScheduleDao implements ScheduleDao {
    
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(Schedule.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public List<Schedule> getSchedules(String userId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Schedule> createSchedules(List<Schedule> schedules) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteSchedules(List<Schedule> schedules) {
        // TODO Auto-generated method stub

    }

}
