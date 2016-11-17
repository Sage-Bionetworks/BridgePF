package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
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
        
        // BRIDGE-1589. Infer scheduled activities derived from non-recurring schedules. Adjust DB activities
        Set<String> schedulePlansWithoutTimes = getSchedulePlansWithoutTimes(scheduledActivities);
        adjustPersistedOneTimeActivities(context, dbActivities, schedulePlansWithoutTimes);
        
        List<ScheduledActivity> saves = updateActivitiesAndCollectSaves(scheduledActivities, dbActivities);
        activityDao.saveActivities(saves);
        
        return orderActivities(scheduledActivities);
    }
    
    /**
     * Schedule has been held over from scheduling so we can deduce from scheduled tasks, which persisted tasks 
     * might have the an issue with their times, or even be duplicates. Note that we do this with existing 
     * schedule plans for performance reasons, rather than looking up each schedule plan, task by task. The 
     * worse case scenario is a user continues to see duplicated one-time tasks. None of this applies to newer
     * accounts where time is already fixed.
     */
    private Set<String> getSchedulePlansWithoutTimes(List<ScheduledActivity> scheduledActivities) {
        if (scheduledActivities == null || scheduledActivities.isEmpty()) {
            return Collections.emptySet();
        }
        return scheduledActivities.stream().filter(act -> {
            return Schedule.isScheduleWithoutTimes(act.getSchedule());
        }).map(ScheduledActivity::getSchedulePlanGuid).collect(toSet());
    }
    
    /**
     * One-time tasks carried over the timestamp of the enrollment event, but that time would change depending on 
     * the time zone submitted by the user (daylight savings time changes). For one-time interval-based tasks 
     * (including persistent tasks), that did not require a time to be set on the schedule, we now set the time 
     * to midnight regardless of time zone. This method fixes already persisted activities rather than trying to 
     * backfill (or until we can backfill). This will lead to setting the same scheduledOn time for duplicates 
     * that will be removed in a later step. 
     */
    private void adjustPersistedOneTimeActivities(ScheduleContext context, List<ScheduledActivity> dbActivities,
            Set<String> oneTimeSchedulePlans) {
        for (int i=0; i < dbActivities.size(); i++) {
            ScheduledActivity activity = dbActivities.get(i);
            if (oneTimeSchedulePlans.contains(activity.getSchedulePlanGuid())) {
                LocalDateTime localDateTime = DateUtils.dateTimeToMidnightUTC(activity.getScheduledOn()).toLocalDateTime();
                String guid = activity.getActivity().getGuid() + ":" + localDateTime;
                activity.setLocalScheduledOn(localDateTime);
                activity.setGuid(guid);
            }
        }
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
        // BRIDGE-1589: After adjusting activity GUIDs' time portion, we will have generated db activities with duplicate 
        // GUIDs in dbActivities, and Maps.uniqueIndex will throw an error because the GUIDs are not all unique. 
        // Build the map manually while de-duplicating the db activities, particularly saving any copy that has 
        // client-persisted state in it (startedOn is good enough here). 
        // Map<String, ScheduledActivity> dbMap = Maps.uniqueIndex(dbActivities, ScheduledActivity::getGuid);
        
        Map<String, ScheduledActivity> dbMap = Maps.newHashMap();
        for (ScheduledActivity dbActivity : dbActivities) {
            
            // If never put in the map, or it has a startedOn value and the existing one doesn't, add it
            ScheduledActivity existing = dbMap.get(dbActivity.getGuid());
            if (existing == null) {
                dbMap.put(dbActivity.getGuid(), dbActivity);
            } else if (existing.getStartedOn() == null && dbActivity.getStartedOn() != null) {
                dbMap.put(dbActivity.getGuid(), dbActivity);
            }
        }
        // At this point all one-time interval tasks without times whether schedules or already persisted, have a 
        // time of midnight, and the db activities have been processed so there is only one and we can determine 
        // if we're adding to an existing activity record or creating a new one.
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
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getCriteriaContext().getClientInfo(),
                context.getCriteriaContext().getStudyIdentifier());
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            if (schedule != null) {
                List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, context);
                List<ScheduledActivity> resolvedActivities = resolveLinks(context, activities);
                scheduledActivities.addAll(resolvedActivities);    
            }
        }
        return scheduledActivities;
    }
    
    private List<ScheduledActivity> resolveLinks(ScheduleContext context, List<ScheduledActivity> activities) {
        return activities.stream().map(schActivity -> {
            Activity activity = schActivity.getActivity();

            if (isReferenceToPublishedSurvey(activity)) {
                Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(
                        context.getCriteriaContext().getStudyIdentifier(), activity.getSurvey().getGuid());

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
