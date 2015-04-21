package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
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

    @Resource(name = "taskDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
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
        Set<String> naturalKeys = Sets.newHashSet();
        Map<String, DateTime> events = createEventsMap(studyIdentifier, user);
        
        DateTime from = DateTime.now().minus(before);
        DateTime until = DateTime.now().plus(after);
        List<Task> results = queryForTasks(studyIdentifier, user.getHealthCode(), from, until);
        
        List<Task> tasks = scheduleTasksForPlans(studyIdentifier, user, events, until);
        for (Task task : tasks) {
            if (!naturalKeys.contains(task.getNaturalKey())) {
                ((DynamoTask)task).setStudyHealthCodeKey(studyIdentifier, user.getHealthCode());
                mapper.save(task);
                results.add(task);
                System.out.println("Adding task: " + task.getNaturalKey());
            } 
            naturalKeys.add(task.getNaturalKey());
        }
        return results;
    }

    @Override
    public void updateTasks(String healthCode, List<Task> tasks) {
    }

    @Override
    public void deleteTasks(String healthCode) {
    }

    private List<Task> queryForTasks(StudyIdentifier studyIdentifier, String healthCode, DateTime from, DateTime until) {
        DynamoDBQueryExpression<DynamoTask> query = createQuery(studyIdentifier, healthCode, from, until);
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        List<Task> results = Lists.newArrayList();
        results.addAll(queryResults);
        return results;
    }
    
    private DynamoDBQueryExpression<DynamoTask> createQuery(StudyIdentifier studyIdentifier, String healthCode, DateTime from, DateTime until) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setStudyHealthCodeKey(studyIdentifier, healthCode);
        
        Condition rangeKeyCondition = new Condition()
            .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
            .withAttributeValueList(new AttributeValue().withS(from.toString()), 
                                    new AttributeValue().withS(until.toString()));
    
        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withHashKeyValues(hashKey)
            .withRangeKeyCondition("scheduledOn", rangeKeyCondition);
        return query;
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
    
    private List<Task> scheduleTasksForPlans(StudyIdentifier studyIdentifier, User user, Map<String,DateTime> events, DateTime until) {
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
