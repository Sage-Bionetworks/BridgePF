package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static java.util.Comparator.comparing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
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
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Component
public class ScheduledActivityService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledActivityService.class);
    
    private static final ScheduleContextValidator VALIDATOR = new ScheduleContextValidator();
    private static final List<SurveyAnswer> EMPTY_ANSWERS = ImmutableList.of();
    
    private ScheduledActivityDao activityDao;
    
    private ActivityEventService activityEventService;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private SurveyService surveyService;
    
    private SurveyResponseService surveyResponseService;
    
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
    public final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    @Autowired
    public final void setSurveyResponseService(SurveyResponseService surveyResponseService) {
        this.surveyResponseService = surveyResponseService;
    }
    
    public List<ScheduledActivity> getScheduledActivities(User user, ScheduleContext context) {
        checkNotNull(user);
        checkNotNull(context);
        
        Validate.nonEntityThrowingException(VALIDATOR, context);
        
        Map<String, DateTime> events = createEventsMap(context);
        
        // Get activities from the scheduler. None of these activities have been saved, some may be new,
        // and some may have already been persisted. They are identified by their runKey.
        ScheduleContext newContext = new ScheduleContext.Builder()
            .withContext(context)
            .withEvents(events).build();
        
        List<ScheduledActivity> scheduledActivities = scheduleActivitiesForPlans(user, newContext);
        List<ScheduledActivity> dbActivities = activityDao.getActivities(context);

        // We need to re-evaluate persisted activities at this point to see if any changes in the user's
        // ScheduleContext has caused them to no longer be valid. We do this without any reference to the 
        // original schedule plan's strategy for selecting activities. 
        
        List<String> scheduledGuids = scheduledActivities.stream()
                .map(ScheduledActivity::getGuid).collect(toList());
        List<String> dbGuids = dbActivities.stream()
                .map(ScheduledActivity::getGuid).collect(toList());
        ScheduledActivityOperations ops = new ScheduledActivityOperations();

        // Are database activities in the list of activities we would schedule? 
        // If they are a deletable status and not in the list, delete them, otherwise, return them.
        for (ScheduledActivity activity : dbActivities) {

            // This one exists in both, add it.
            if (scheduledGuids.contains(activity.getGuid())) {
                ops.result(activity); // It's in both, use the db version
            } else {
                if (activity.getStatus() == ScheduledActivityStatus.STARTED) {
                    ops.result(activity); // It's in both, use the db version
                } else {
                    ops.delete(activity); // It's in db but not produced by scheduler, delete it
                }
            }
        }
        // Are scheduled activities already in db? If they are brand new, persist them and also return them
        for (ScheduledActivity activity : scheduledActivities) {
            if (!dbGuids.contains(activity.getGuid())) {
                ops.save(activity); // It's in db but not produced by scheduler, delete it
            }
        }
        
        // delete
        if (!ops.getDeletables().isEmpty()) {
            activityDao.deleteActivities(ops.getDeletables().stream().collect(toList()));    
        }
        // save
        if (!ops.getSavables().isEmpty()) {
            // if a survey activity, it may need a survey response generated (currently we're not using this though).
            for (ScheduledActivity schActivity : ops.getSavables()) {
                // If they have not been persisted yet, get each activity one by one, create a survey 
                // response for survey activities, and add the activities to the list of activities to save.
                Activity activity = createResponseActivityIfNeeded(context.getStudyIdentifier(),
                        context.getHealthCode(), schActivity.getActivity());
                schActivity.setActivity(activity);
            }
            activityDao.saveActivities(ops.getSavables().stream().collect(toList()));
        }
        return ops.getResults().stream()
                .filter(activity -> ScheduledActivityStatus.VISIBLE_STATUSES.contains(activity.getStatus()))
                .sorted(comparing(ScheduledActivity::getScheduledOn))
                .collect(toList());
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
                ScheduledActivity dbActivity = activityDao.getActivity(healthCode, schActivity.getGuid());
                if (schActivity.getStartedOn() != null) {
                    dbActivity.setStartedOn(schActivity.getStartedOn());
                    dbActivity.setHidesOn(new Long(Long.MAX_VALUE));
                }
                if (schActivity.getFinishedOn() != null) {
                    dbActivity.setFinishedOn(schActivity.getFinishedOn());
                    activityEventService.publishActivityFinishedEvent(dbActivity);
                }
                activitiesToSave.add(dbActivity);
            }
        }
        if (!activitiesToSave.isEmpty()) {
            activityDao.updateActivities(healthCode, activitiesToSave);    
        }
    }
    
    public void deleteActivitiesForUser(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        activityDao.deleteActivitiesForUser(healthCode);
    }
    
    public void deleteActivitiesForSchedulePlan(String schedulePlanGuid) {
        checkArgument(isNotBlank(schedulePlanGuid));
        
        activityDao.deleteActivitiesForSchedulePlan(schedulePlanGuid);
    }
    
    /**
     * @param user
     * @return
     */
    private Map<String, DateTime> createEventsMap(ScheduleContext context) {
        Map<String,DateTime> events = activityEventService.getActivityEventMap(context.getHealthCode());
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
        UserConsent consent = userConsentDao.getActiveUserConsent(context.getHealthCode(), context.getStudyIdentifier());
        Map<String,DateTime> newEvents = Maps.newHashMap();
        newEvents.putAll(events);
        newEvents.put("enrollment", new DateTime(consent.getSignedOn()));
        logger.warn("Enrollment missing from activity event table, pulling from consent record");
        return newEvents;
    }
    
    private List<ScheduledActivity> scheduleActivitiesForPlans(User user, ScheduleContext context) {
        StudyIdentifier studyId = new StudyIdentifierImpl(user.getStudyKey());
        
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getClientInfo(), studyId);
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            if (schedule != null) {
                List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, context);
                scheduledActivities.addAll(activities);
            }
        }
        return scheduledActivities;
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
