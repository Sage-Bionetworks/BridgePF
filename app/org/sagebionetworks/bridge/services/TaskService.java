package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.SurveyReference.SURVEY_PATH_FRAGMENT;
import static org.sagebionetworks.bridge.models.schedules.SurveyReference.SURVEY_RESPONSE_PATH_FRAGMENT;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SchedulerFactory;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskScheduler;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class TaskService {
    
    public static final int DEFAULT_EXPIRES_ON_DAYS = 2;
    public static final int MAX_EXPIRES_ON_DAYS = 4;
    
    private static final List<SurveyAnswer> EMPTY_ANSWERS = ImmutableList.of();
    
    private TaskDao taskDao;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private SurveyService surveyService;
    
    private SurveyResponseService surveyResponseService;
    
    @Autowired
    public void setTaskDao(TaskDao taskDao) {
        this.taskDao = taskDao;
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
    
    public List<Task> getTasks(User user, DateTime endsOn) {
        checkNotNull(user);
        checkNotNull(endsOn);
        
        DateTime now = DateTime.now();
        if (endsOn.isBefore(now)) {
            throw new BadRequestException("End timestamp must be after the time of the request");
        } else if (endsOn.minusDays(MAX_EXPIRES_ON_DAYS).isAfter(now)) {
            throw new BadRequestException("Task request window must be "+MAX_EXPIRES_ON_DAYS+" days or less");
        }
        
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());
        List<Task> tasksToSave = Lists.newArrayList();

        Map<String,List<Task>> scheduledTasks = scheduleTasksForPlans(user, endsOn);
        for (String runKey : scheduledTasks.keySet()) {
            if (taskDao.taskRunHasNotOccurred(user.getHealthCode(), runKey)) {
                for (Task task : scheduledTasks.get(runKey)) {
                    Activity activity = createResponseActivityIfNeeded(
                        studyId, user.getHealthCode(), task.getActivity());
                    task.setActivity(activity);
                    tasksToSave.add(task);
                }
            }
        }
        taskDao.saveTasks(user.getHealthCode(), tasksToSave);
        return taskDao.getTasks(user.getHealthCode(), endsOn);
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
    private Map<String, DateTime> createEventsMap(User user) {
        Map<String,DateTime> events = Maps.newHashMap();
        UserConsent consent = userConsentDao.getUserConsent(user.getHealthCode(), new StudyIdentifierImpl(user.getStudyKey()));
        events.put("enrollment", new DateTime(consent.getSignedOn()));
        return events;
    }
   
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
                map.put(tasks.get(0).getRunKey(), tasks);
            }
        }
        return map;
    }
    
    private Activity createResponseActivityIfNeeded(StudyIdentifier studyIdentifier, String healthCode, Activity activity) {
        if ((activity.getActivityType() != ActivityType.SURVEY)) {
            return activity;
        }
        String baseUrl = activity.getRef().split(SURVEY_PATH_FRAGMENT)[0];
        
        SurveyReference ref = activity.getSurvey();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref);
        if (keys.getCreatedOn() == 0L) {
            keys = surveyService.getSurveyMostRecentlyPublishedVersion(studyIdentifier, ref.getGuid());
        }   
        SurveyResponseView response = surveyResponseService.createSurveyResponse(keys, healthCode, EMPTY_ANSWERS);
        String url = baseUrl + SURVEY_RESPONSE_PATH_FRAGMENT + response.getIdentifier();
        return new Activity(activity.getLabel(), url);
    }
    
}
