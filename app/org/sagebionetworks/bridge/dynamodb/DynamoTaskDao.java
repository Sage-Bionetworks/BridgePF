package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DynamoTaskDao implements TaskDao {
    
    private static final Comparator<Task> TASK_COMPARATOR = new Comparator<Task>() {
        @Override 
        public int compare(Task task1, Task task2) {
            int result = (int)(task1.getScheduledOn() - task2.getScheduledOn());
            if (result == 0) {
                result = task1.getActivity().getLabel().compareTo(task2.getActivity().getLabel());
            }
            return result;
        }
    };
    
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

    /**
     * Get all undeleted tasks up to the endsOn date. Only return tasks that have expired (issued but not started 
     * before the expiration timestamp) within the startsOn to endsOn time window. The client may adjust this 
     * window to include expired tasks for a period before the time of the call, or use "now" to eliminate 
     * expired tasks entirely.
     */
    @Override
    public List<Task> getTasks(User user, Period startsOn, Period endsOn) {
        Set<String> naturalKeys = Sets.newHashSet();
        
        DateTime from = DateTime.now().minus(startsOn);
        DateTime until = DateTime.now().plus(endsOn);
        
        List<Task> tasksToSave = Lists.newArrayList();
        List<Task> dbTasks = queryForTasks(user, from, until);
        List<Task> scheduledTasks = scheduleTasksForPlans(user, from, until);
        
        // Reconcile saved and scheduler tasks, saving the unsaved tasks
        for (Task task : dbTasks) {
            naturalKeys.add(task.getNaturalKey());
        }
        for (Task task : scheduledTasks) {
            if (naturalKeys.add(task.getNaturalKey())) {
                tasksToSave.add(task);
                dbTasks.add(task);
            }
        }
        
        List<FailedBatch> failures = mapper.batchSave(tasksToSave);
        BridgeUtils.ifFailuresThrowException(failures);
        
        Collections.sort(dbTasks, TASK_COMPARATOR);
        return dbTasks;
    }
    
    @Override
    public void updateTasks(String healthCode, List<Task> tasks) {
    }

    @Override
    public void deleteTasks(String healthCode) {
    }

    private List<Task> queryForTasks(User user, DateTime from, DateTime until) {
        DynamoDBQueryExpression<DynamoTask> query = createQuery(user, from, until);
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        List<Task> results = Lists.newArrayList();
        results.addAll(queryResults);
        return results;
    }
    
    private DynamoDBQueryExpression<DynamoTask> createQuery(User user, DateTime from, DateTime until) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(user.getHealthCode());
        
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
    private Map<String, DateTime> createEventsMap(User user) {
        Map<String,DateTime> events = Maps.newHashMap();
        UserConsent consent = userConsentDao.getUserConsent(user.getHealthCode(), new StudyIdentifierImpl(user.getStudyKey()));
        events.put("enrollment", new DateTime(consent.getSignedOn()));
        return events;
    }
    
    /**
     * Find all tasks from all schedules generated by all schedule plans, returning a list of tasks that are 
     * within the given time window. These tasks may or may not be persisted.
     * @param studyIdentifier
     * @param user
     * @param events
     * @param from
     * @param until
     * @return
     */
    private List<Task> scheduleTasksForPlans(User user, DateTime from, DateTime until) {
        Map<String, DateTime> events = createEventsMap(user);
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());
        List<Task> tasks = Lists.newArrayList();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(studyId);
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(studyId, plan, user);
            TaskScheduler scheduler = SchedulerFactory.getScheduler(plan.getGuid(), schedule);
            
            for (Task task : scheduler.getTasks(events, until)) {
                if (from.isEqual(task.getScheduledOn()) || from.isBefore(task.getScheduledOn())) {
                    ((DynamoTask)task).setHealthCode(user.getHealthCode());
                    tasks.add(task);
                }
            }
        }
        return tasks;
    }
    
}
