package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SchedulerFactory;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskScheduler;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ActivityService;
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
    
    private DynamoDBMapper mapper;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private ActivityService activityService;

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
    
    @Autowired
    public void setActivityService(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> getTasks(User user, DateTime endsOn) {
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());
        
        Map<String,List<Task>> scheduledTasks = scheduleTasksForPlans(user, endsOn);
        List<Task> tasksToSave = Lists.newArrayList();
        for (String runKey : scheduledTasks.keySet()) {
            if (taskHasNotBeenSaved(user.getHealthCode(), runKey)) {
                for (Task task : scheduledTasks.get(runKey)) {
                    Activity activity = activityService.createResponseActivityIfNeeded(
                        studyId, user.getHealthCode(), task.getActivity());
                    task.setActivity(activity);
                    tasksToSave.add(task);
                }
            }
        }
        if (!tasksToSave.isEmpty()) {
            List<FailedBatch> failures = mapper.batchSave(tasksToSave);
            BridgeUtils.ifFailuresThrowException(failures);
        }
        return getTasksWithoutScheduling(user);
    }
    
    /** {@inheritDoc} */
    @Override
    public List<Task> getTasksWithoutScheduling(User user) {
        List<Task> tasks = queryForTasks(user.getHealthCode());
        Collections.sort(tasks, TASK_COMPARATOR);
        return tasks;
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
                Task dbTask = mapper.load(hashKey);
                
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
        
        List<FailedBatch> failures = mapper.batchDelete(tasksToDelete);
        BridgeUtils.ifFailuresThrowException(failures);
    }

    private List<Task> queryForTasks(String healthCode) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(healthCode);

        // Exclude everything hidden before *now*
        AttributeValue attribute = new AttributeValue().withN(Long.toString(DateTime.now().getMillis()));
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.GT)
            .withAttributeValueList(attribute);

        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withQueryFilterEntry("hidesOn", condition)
            .withHashKeyValues(hashKey);

        PaginatedQueryList<DynamoTask> queryResults = mapper.query(DynamoTask.class, query);
        List<Task> tasks = Lists.newArrayList();
        tasks.addAll(queryResults);
        return tasks;
    }
    
    /**
     * Determine if this task was part of a scheduling run that has already been saved to the database 
     * (all the activities from one schedule plan will have the same taskRun key).
     * 
     * @param healthCode
     * @param taskRunKey
     * @return
     */
    private boolean taskHasNotBeenSaved(String healthCode, String taskRunKey) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(healthCode);
        hashKey.setRunKey(taskRunKey);
        
        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withHashKeyValues(hashKey);

        return (mapper.count(DynamoTask.class, query) == 0);
    }

    /**
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
     * 
     * @param user
     * @param endsOn
     * @return
     */
    private Map<String,List<Task>> scheduleTasksForPlans(User user, DateTime endsOn) {
        Map<String,List<Task>> map = Maps.newHashMap();
        Map<String, DateTime> events = createEventsMap(user);
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());

        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(studyId);
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(studyId, plan, user);
            TaskScheduler scheduler = SchedulerFactory.getScheduler(plan.getGuid(), schedule);

            List<Task> tasks = scheduler.getTasks(events, endsOn);
            if (!tasks.isEmpty()) {
                for (Task task : tasks) {
                    task.setHealthCode(user.getHealthCode());
                }
                map.put(tasks.get(0).getRunKey(), tasks);
            }
        }
        return map;
    }

}
