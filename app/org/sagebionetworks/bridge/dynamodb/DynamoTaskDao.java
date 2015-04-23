package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
import org.sagebionetworks.bridge.models.schedules.TaskStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
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

    private static Function<Task,String> TASK_TO_GUID = new Function<Task, String>() {
        @Override public String apply(Task task) {
            return task.getGuid();
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
        DateTime from = DateTime.now().minus(startsOn);
        DateTime until = DateTime.now().plus(endsOn);
        
        List<Task> dbTasks = queryForTasks(user.getHealthCode(), from, until);
        List<Task> scheduledTasks = scheduleTasksForPlans(user, from, until);
        Set<String> guids = new HashSet<>(Lists.transform(dbTasks, TASK_TO_GUID));
        
        // Reconcile saved and scheduler tasks, saving the unsaved tasks
        List<Task> tasksToSave = Lists.newArrayList();
        for (Task task : scheduledTasks) {
            // If it adds to the GUIDs set, it is new, so save it, UNLESS it is already expired 
            // and the scheduled start time is not in the bounds of the query... in other words, 
            // the user never saw this task at all. These are returned from the scheduler, but 
            // don't save them in DDB. 
            if (guids.add(task.getGuid()) && !taskExpiredOutsideOfTimeWindow(task, from)) {
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
        List<Task> tasksToSave = Lists.newArrayList();
        for (Task task : tasks) {
            if (task != null) {
                Task dbTask = mapper.load(task);
                if (dbTask != null && task.getStartedOn() != null || task.getFinishedOn() != null) {
                    if (task.getStartedOn() != null) {
                        dbTask.setStartedOn(task.getStartedOn());
                    }
                    if (task.getFinishedOn() != null) {
                        dbTask.setFinishedOn(task.getFinishedOn());
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
    
    @Override
    public void deleteTasks(String healthCode) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>().withHashKeyValues(hashKey);
        
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        List<DynamoTask> tasksToDelete = Lists.newArrayListWithCapacity(queryResults.size());
        tasksToDelete.addAll(queryResults);
        
        List<FailedBatch> failures = mapper.batchDelete(tasksToDelete);
        BridgeUtils.ifFailuresThrowException(failures);
    }
    
    private boolean taskExpiredOutsideOfTimeWindow(Task task, DateTime from) {
        return (task.getStatus() == TaskStatus.EXPIRED && from.isAfter(task.getScheduledOn()));
    }

    private List<Task> queryForTasks(String healthCode, DateTime from, DateTime until) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(healthCode);
        
        Condition rangeKeyCondition = new Condition()
            .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
            .withAttributeValueList(new AttributeValue().withN(new Long(from.getMillis()).toString()), 
                                    new AttributeValue().withN(new Long(until.getMillis()).toString()));
    
        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withHashKeyValues(hashKey)
            .withConsistentRead(false) 
            .withRangeKeyCondition("scheduledOn", rangeKeyCondition);
        
        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        
        List<Task> results = Lists.newArrayList();
        for (DynamoTask task : queryResults) {
            results.add(task);
        }
        return results;
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
