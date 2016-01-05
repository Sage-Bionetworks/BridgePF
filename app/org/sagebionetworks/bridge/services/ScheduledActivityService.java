package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
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
    
    private SubpopulationService subpopService;
    
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
    @Autowired
    public final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
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
        Multimap<String,ScheduledActivity> scheduledActivities = scheduleActivitiesForPlans(newContext);
        
        List<ScheduledActivity> scheduledActivitiesToSave = Lists.newArrayList();
        for (String runKey : scheduledActivities.keySet()) {
            if (activityDao.activityRunHasNotOccurred(context.getHealthCode(), runKey)) {
                for (ScheduledActivity schActivity : scheduledActivities.get(runKey)) {
                    // If they have not been persisted yet, get each activity one by one, create a survey 
                    // response for survey activities, and add the activities to the list of activities to save.
                    Activity activity = createResponseActivityIfNeeded(
                        context.getStudyIdentifier(), context.getHealthCode(), schActivity.getActivity());
                    schActivity.setActivity(activity);
                    scheduledActivitiesToSave.add(schActivity);
                }
            }
        }
        // Finally, save these new activities
        activityDao.saveActivities(scheduledActivitiesToSave);
        
        // Now read back the activities from the database to pick up persisted startedOn, finishedOn values, 
        // but filter based on the endsOn time from the query. If the client dynamically adjusts the 
        // lookahead window from a large number of days to a small number of days, the client would 
        // still get back all the activities scheduled into the longer time period, so we filter these.
        return activityDao.getActivities(context).stream().filter(schActivity -> {
            return !schActivity.getScheduledOn().isAfter(context.getEndsOn());
        }).collect(Collectors.toList());
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
                    dbActivity.setHidesOn(schActivity.getFinishedOn());
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
     * We have back-filled this event, so this should no longer be needed, but it is left here just in case.
     * @param user
     * @param events
     * @return
     */
    private Map<String, DateTime> createEnrollmentEventFromConsent(ScheduleContext context, Map<String, DateTime> events) {
        // This should no longer happen, but in case a record was never migrated, go back to the consents to find the 
        // enrollment date. It's the earliest of all the signature dates.
        long signedOn = Long.MAX_VALUE;
        List<Subpopulation> subpops = subpopService.getSubpopulations(context.getStudyIdentifier());
        for (Subpopulation subpop : subpops) {
            UserConsent consent = userConsentDao.getActiveUserConsent(context.getHealthCode(), subpop.getGuid());
            if (consent != null && consent.getSignedOn() < signedOn) {
                signedOn = consent.getSignedOn();
            }
        }
        Map<String,DateTime> newEvents = Maps.newHashMap();
        newEvents.putAll(events);
        newEvents.put("enrollment", new DateTime(signedOn));
        logger.warn("Enrollment missing from activity event table, pulling from consent records");
        return newEvents;
    }
   
    private Multimap<String,ScheduledActivity> scheduleActivitiesForPlans(ScheduleContext context) {
        StudyIdentifier studyId = context.getStudyIdentifier();
        
        Multimap<String,ScheduledActivity> scheduledActivities = ArrayListMultimap.create();
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getClientInfo(), studyId);
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            
            List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, context);
            for (ScheduledActivity activity : activities) {
                scheduledActivities.put(activity.getRunKey(), activity);
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
