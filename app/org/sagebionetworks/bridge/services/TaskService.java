package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

@Component
public class TaskService {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);
    
    private static final ScheduleContextValidator VALIDATOR = new ScheduleContextValidator();
    private static final List<SurveyAnswer> EMPTY_ANSWERS = ImmutableList.of();
    
    private TaskDao taskDao;
    
    private TaskEventService taskEventService;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private SurveyService surveyService;
    
    private SurveyResponseService surveyResponseService;
    
    @Autowired
    public void setTaskDao(TaskDao taskDao) {
        this.taskDao = taskDao;
    }
    @Autowired
    public void setTaskEventService(TaskEventService taskEventService) {
        this.taskEventService = taskEventService;
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
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    @Autowired
    public void setSurveyResponseService(SurveyResponseService surveyResponseService) {
        this.surveyResponseService = surveyResponseService;
    }
    
    public List<Task> getTasks(User user, ScheduleContext context) {
        checkNotNull(user);
        checkNotNull(context);
        
        Validate.nonEntityThrowingException(VALIDATOR, context);
        
        Map<String, DateTime> events = createEventsMap(context);
        
        // Get tasks from the scheduler. None of these tasks have been saved, some may be new,
        // and some may have already been persisted. They are identified by their runKey.
        ScheduleContext newContext = new ScheduleContext.Builder()
            .withContext(context)
            .withEvents(events).build();
        Multimap<String,Task> scheduledTasks = scheduleTasksForPlans(user, newContext);
        
        List<Task> tasksToSave = Lists.newArrayList();
        for (String runKey : scheduledTasks.keySet()) {
            if (taskDao.taskRunHasNotOccurred(context.getHealthCode(), runKey)) {
                for (Task task : scheduledTasks.get(runKey)) {
                    // If they have not been persisted yet, get each task one by one, create a survey 
                    // response for survey tasks, and add the tasks to the list of tasks to save.
                    Activity activity = createResponseActivityIfNeeded(
                        context.getStudyIdentifier(), context.getHealthCode(), task.getActivity());
                    task.setActivity(activity);
                    tasksToSave.add(task);
                }
            }
        }
        // Finally, save these new tasks
        taskDao.saveTasks(tasksToSave);
        
        // Now read back the tasks from the database to pick up persisted startedOn, finishedOn values, 
        // but filter based on the endsOn time from the query. If the client dynamically adjusts the 
        // lookahead window from a large number of days to a small number of days, the client would 
        // still get back all the tasks scheduled into the longer time period, so we filter these.
        return taskDao.getTasks(context).stream().filter(task -> {
            return !task.getScheduledOn().isAfter(context.getEndsOn());
        }).collect(Collectors.toList());
    }
    
    public void updateTasks(String healthCode, List<Task> tasks) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(tasks);
        for (int i=0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task == null) {
                throw new BadRequestException("A task in the array is null");
            }
            if (task.getGuid() == null) {
                throw new BadRequestException(String.format("Task #%s has no GUID", i));
            }
        }
        taskDao.updateTasks(healthCode, tasks);
    }
    
    public void deleteTasks(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        taskDao.deleteTasks(healthCode);
    }
    
    /**
     * @param user
     * @return
     */
    private Map<String, DateTime> createEventsMap(ScheduleContext context) {
        Map<String,DateTime> events = taskEventService.getTaskEventMap(context.getHealthCode());
        if (!events.containsKey("enrollment")) {
            return createEnrollmentEventFromConsent(context, events);
        }
        return events;
    }
    
    /**
     * No events have been recorded for this participant, so get an enrollment event from the consent records.
     * We have back-filled this event, so this should no longer be needed, but is left here just in case.
     * @param user
     * @param events
     * @return
     */
    private Map<String, DateTime> createEnrollmentEventFromConsent(ScheduleContext context, Map<String, DateTime> events) {
        UserConsent consent = userConsentDao.getUserConsent(context.getHealthCode(), context.getStudyIdentifier());
        Map<String,DateTime> newEvents = Maps.newHashMap();
        newEvents.putAll(events);
        newEvents.put("enrollment", new DateTime(consent.getSignedOn()));
        logger.warn("Enrollment missing from task event table, pulling from consent record");
        return newEvents;
    }
   
    private Multimap<String,Task> scheduleTasksForPlans(User user, ScheduleContext context) {
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());
        
        Multimap<String,Task> scheduledTasks = ArrayListMultimap.create();
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getClientInfo(), studyId);
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(studyId, plan, user);
            
            List<Task> tasks = schedule.getScheduler().getTasks(plan, context);
            for (Task task : tasks) {
                scheduledTasks.put(task.getRunKey(), task);
            }
        }
        return scheduledTasks;
    }
    
    private Activity createResponseActivityIfNeeded(StudyIdentifier studyIdentifier, String healthCode, Activity activity) {
        // If this activity is a task activity, or the survey response for this survey has already been determined
        // and added to the activity, then do not generate a survey response for this activity.
        if (activity.getActivityType() == ActivityType.TASK || activity.getSurveyResponse() != null) {
            return activity;
        }
        
        // Get a survey reference and if necessary, resolve the timestamp to use for the survey
        SurveyReference ref = activity.getSurvey();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref);
        if (keys.getCreatedOn() == 0L) {
            keys = surveyService.getSurveyMostRecentlyPublishedVersion(studyIdentifier, ref.getGuid());
        }   
        
        // Now create a response for that specific survey version
        SurveyResponseView response = surveyResponseService.createSurveyResponse(keys, healthCode, EMPTY_ANSWERS, null);
        
        // And reconstruct the activity with that survey instance as well as the new response object.
        return new Activity.Builder()
            .withLabel(activity.getLabel())
            .withLabelDetail(activity.getLabelDetail())
            .withSurvey(response.getSurvey().getIdentifier(), keys.getGuid(), ref.getCreatedOn())
            .withSurveyResponse(response.getResponse().getIdentifier())
            .build();
    }
    
}
