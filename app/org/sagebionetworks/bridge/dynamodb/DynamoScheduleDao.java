package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ScheduleDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.validators.ScheduleValidator;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoScheduleDao implements ScheduleDao {
    
    private ScheduleValidator VALIDATOR = new ScheduleValidator();
    
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSchedule.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public List<Schedule> getSchedules(Study study, User user) {
        DynamoSchedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(study, user);
        
        DynamoDBQueryExpression<DynamoSchedule> query = new DynamoDBQueryExpression<DynamoSchedule>();
        query.withHashKeyValues(schedule);
        
        return new ArrayList<Schedule>(mapper.queryPage(DynamoSchedule.class, query).getResults());
    }

    @Override
    public List<Schedule> createSchedules(List<Schedule> schedules) {
        for (Schedule schedule : schedules) {
            VALIDATOR.validate(schedule);
            schedule.setGuid(BridgeUtils.generateGuid());
        }
        List<FailedBatch> failures = mapper.batchSave(schedules);
        BridgeUtils.ifFailuresThrowException(failures);
        return schedules;
    }

    @Override
    public void deleteSchedules(SchedulePlan plan) {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(plan.getGuid()));
        scan.addFilterCondition("schedulePlanGuid", condition);
        
        List<DynamoSchedule> schedules = mapper.scan(DynamoSchedule.class, scan);
        List<FailedBatch> failures = mapper.batchDelete(schedules);
        BridgeUtils.ifFailuresThrowException(failures);
    }

}
