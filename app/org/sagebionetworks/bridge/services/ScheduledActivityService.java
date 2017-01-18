package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class ScheduledActivityService {
    
    private static final String ENROLLMENT = "enrollment";

    private static final ScheduleContextValidator VALIDATOR = new ScheduleContextValidator();
    
    private ScheduledActivityDao activityDao;
    
    private ActivityEventService activityEventService;
    
    private SchedulePlanService schedulePlanService;
    
    private SurveyService surveyService;
    
    @Autowired
    public final void setScheduledActivityDao(ScheduledActivityDao activityDao) {
        this.activityDao = activityDao;
    }
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    public final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    public List<ScheduledActivity> getScheduledActivities(ScheduleContext context) {
        checkNotNull(context);
        
        Validate.nonEntityThrowingException(VALIDATOR, context);
        
        // Add events for scheduling
        Map<String, DateTime> events = createEventsMap(context);
        ScheduleContext newContext = new ScheduleContext.Builder().withContext(context).withEvents(events).build();
        
        // Get scheduled activities, persisted activities, and compare them
        List<ScheduledActivity> scheduledActivities = scheduleActivitiesForPlans(newContext);
        List<ScheduledActivity> dbActivities = activityDao.getActivities(newContext.getZone(), scheduledActivities);
        
        List<ScheduledActivity> saves = updateActivitiesAndCollectSaves(scheduledActivities, dbActivities);
        activityDao.saveActivities(saves);
        
        return orderActivities(scheduledActivities);
    }
    
    public void updateScheduledActivities(String healthCode, List<ScheduledActivity> scheduledActivities) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(scheduledActivities);
        
        List<ScheduledActivity> activitiesToSave = Lists.newArrayListWithCapacity(scheduledActivities.size());
        for (int i=0; i < scheduledActivities.size(); i++) {
            ScheduledActivity schActivity = scheduledActivities.get(i);
            if (schActivity == null) {
                throw new BadRequestException("A task in the array is null");
            }
            if (schActivity.getGuid() == null) {
                throw new BadRequestException(String.format("Task #%s has no GUID", i));
            }
            if (schActivity.getStartedOn() != null || schActivity.getFinishedOn() != null) {
                // We do not need to add the time zone here. Not returning these to the user.
                ScheduledActivity dbActivity = activityDao.getActivity(null, healthCode, schActivity.getGuid());
                if (schActivity.getStartedOn() != null) {
                    dbActivity.setStartedOn(schActivity.getStartedOn());
                }
                if (schActivity.getFinishedOn() != null) {
                    dbActivity.setFinishedOn(schActivity.getFinishedOn());
                    activityEventService.publishActivityFinishedEvent(dbActivity);
                }
                activitiesToSave.add(dbActivity);
            }
        }
        activityDao.updateActivities(healthCode, activitiesToSave);
    }
    
    public void deleteActivitiesForUser(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        activityDao.deleteActivitiesForUser(healthCode);
    }
    
    protected List<ScheduledActivity> updateActivitiesAndCollectSaves(List<ScheduledActivity> scheduledActivities, List<ScheduledActivity> dbActivities) {
        Map<String, ScheduledActivity> dbMap = Maps.uniqueIndex(dbActivities, ScheduledActivity::getGuid);
        
        // Find activities that have been scheduled, but not saved. If they have been scheduled and saved,
        // replace the scheduled activity with the database activity so the existing state is returned to 
        // user (startedOn/finishedOn). Don't save expired tasks though.
        List<ScheduledActivity> saves = Lists.newArrayList();
        for (int i=0; i < scheduledActivities.size(); i++) {
            ScheduledActivity activity = scheduledActivities.get(i);

            ScheduledActivity dbActivity = dbMap.get(activity.getGuid());
            if (dbActivity != null) {
                scheduledActivities.set(i, dbActivity);
            } else if (activity.getStatus() != ScheduledActivityStatus.EXPIRED) {
                saves.add(activity);
            }
        }
        return saves;
    }
    
    protected List<ScheduledActivity> orderActivities(List<ScheduledActivity> activities) {
        return activities.stream()
            .filter(activity -> ScheduledActivityStatus.VISIBLE_STATUSES.contains(activity.getStatus()))
            .sorted(comparing(ScheduledActivity::getScheduledOn))
            .collect(toImmutableList());
    }
    
    private Map<String, DateTime> createEventsMap(ScheduleContext context) {
        Map<String,DateTime> events = activityEventService.getActivityEventMap(context.getCriteriaContext().getHealthCode());
        if (!events.containsKey(ENROLLMENT)) {
            events = new ImmutableMap.Builder<String, DateTime>().putAll(events)
                    .put(ENROLLMENT, context.getAccountCreatedOn()).build();
        }
        return events;
    }
    
    protected List<ScheduledActivity> scheduleActivitiesForPlans(ScheduleContext context) {
        // Reduce the number of calls to get a survey, as repeating tasks will make several calls
        Map<String,Survey> surveyCache = Maps.newHashMap();
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getCriteriaContext().getClientInfo(),
                context.getCriteriaContext().getStudyIdentifier());
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            if (schedule != null) {
                List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, context);
                List<ScheduledActivity> resolvedActivities = resolveLinks(context, surveyCache, activities);
                scheduledActivities.addAll(resolvedActivities);    
            }
        }
        return scheduledActivities;
    }
    
    private List<ScheduledActivity> resolveLinks(ScheduleContext context, Map<String, Survey> surveyCache,
            List<ScheduledActivity> activities) {

        return activities.stream().map(schActivity -> {
            Activity activity = schActivity.getActivity();

            if (isReferenceToPublishedSurvey(activity)) {
                Survey survey = surveyCache.get(activity.getSurvey().getGuid());
                if (survey == null) {
                    survey = surveyService.getSurveyMostRecentlyPublishedVersion(
                            context.getCriteriaContext().getStudyIdentifier(), activity.getSurvey().getGuid());
                    surveyCache.put(survey.getGuid(), survey);
                }
                Activity resolvedActivity = new Activity.Builder().withActivity(activity)
                        .withSurvey(survey.getIdentifier(), survey.getGuid(), new DateTime(survey.getCreatedOn()))
                        .build();
                schActivity.setActivity(resolvedActivity);
            }
            return schActivity;
        }).collect(toList());
    }
    
    private boolean isReferenceToPublishedSurvey(Activity activity) {
        return (activity.getActivityType() == ActivityType.SURVEY && activity.getSurvey().getCreatedOn() == null);
    }
    
}
