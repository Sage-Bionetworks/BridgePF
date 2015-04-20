package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SchedulerFactory;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskScheduler;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DynamoTaskDao implements TaskDao {
    
    private DynamoDBMapper mapper;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoTask.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Autowired
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    
    @Override
    public List<Task> getTasks(StudyIdentifier studyIdentifier, User user, Period before, Period after) {
        Set<String> guids = Sets.newHashSet();
        
        Map<String, DateTime> events = createEventsMap(studyIdentifier, user);
        DateTime from = DateTime.now().minus(before);
        DateTime until = DateTime.now().plus(after);
        
        List<Task> results = Lists.newArrayList();
        
        // Get tasks from database. 
        DynamoTask hashKey = new DynamoTask();
        hashKey.setStudyHealthCodeKey(studyIdentifier, user.getHealthCode());
        
        Condition rangeKeyCondition = new Condition()
            .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
            .withAttributeValueList(new AttributeValue().withS(from.toString()), 
                                    new AttributeValue().withS(until.toString()));
    
        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withHashKeyValues(hashKey)
            .withRangeKeyCondition("scheduledOn", rangeKeyCondition);
        
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        
        // Get tasks from scheduler
        List<Task> tasks = getTasksForPlans(studyIdentifier, user, events, until);
        for (Task task : tasks) {
            if (!guids.contains(task.getGuid())) {
                // create it, then add it.
                mapper.save(task);
                results.add(task);
            } 
            guids.add(task.getGuid());
        }
        
        return results;
    }

    @Override
    public void updateTasks(String healthCode, List<Task> tasks) {
    }

    @Override
    public void deleteTasks(String healthCode) {
    }

    /**
     * TODO: Right now this has to come from the consent record... we want a separate even table for this.
     * @param studyIdentifier
     * @param user
     * @return
     */
    private Map<String, DateTime> createEventsMap(StudyIdentifier studyIdentifier, User user) {
        Map<String,DateTime> events = Maps.newHashMap();
        UserConsent consent = userConsentDao.getUserConsent(user.getHealthCode(), studyIdentifier);
        events.put("enrollment", new DateTime(consent.getSignedOn()));
        return events;
    }
    
    private List<Task> getTasksForPlans(StudyIdentifier studyIdentifier, User user, Map<String,DateTime> events, DateTime until) {
        List<Task> tasks = Lists.newArrayList();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(studyIdentifier);
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(studyIdentifier, plan, user);
            TaskScheduler scheduler = SchedulerFactory.getScheduler(plan.getGuid(), schedule);
            
            // reconcile these tasks with the tasks you retrieved from the database.
            tasks.addAll(scheduler.getTasks(events, until));
        }
        return tasks;
    }
    
}
