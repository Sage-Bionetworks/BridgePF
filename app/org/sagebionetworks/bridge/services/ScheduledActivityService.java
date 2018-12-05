package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.CLIENT_DATA_MAX_BYTES;
import static org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus.UPDATABLE_STATUSES;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;
import static org.sagebionetworks.bridge.validators.ScheduleContextValidator.MAX_DATE_RANGE_IN_DAYS;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RangeTuple;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ScheduledActivityService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledActivityService.class);
    
    static final Predicate<ScheduledActivity> V3_FILTER = activity -> {
        return ScheduledActivityStatus.VISIBLE_STATUSES.contains(activity.getStatus());
    };
    
    static final Predicate<ScheduledActivity> V4_FILTER = activity -> {
        return activity.getStatus() != ScheduledActivityStatus.DELETED;
    };
    
    private static final String EITHER_BOTH_DATES_OR_NEITHER = "Only one date of a date range provided (both scheduledOnStart and scheduledOnEnd required)";
    
    private static final String INVALID_TIME_RANGE = "scheduledOnStart later in time than scheduledOnEnd";

    private static final String ENROLLMENT = "enrollment";

    private static final ScheduleContextValidator VALIDATOR = new ScheduleContextValidator();

    private ScheduledActivityDao activityDao;

    private ActivityEventService activityEventService;

    private CompoundActivityDefinitionService compoundActivityDefinitionService;

    private SchedulePlanService schedulePlanService;

    private UploadSchemaService schemaService;

    private SurveyService surveyService;
    
    private AppConfigService appConfigService;
    
    @Autowired
    final void setScheduledActivityDao(ScheduledActivityDao activityDao) {
        this.activityDao = activityDao;
    }
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    /**
     * Compound Activity Definition service, used to resolve compound activities (references) in activity schedules.
     */
    @Autowired
    final void setCompoundActivityDefinitionService(
            CompoundActivityDefinitionService compoundActivityDefinitionService) {
        this.compoundActivityDefinitionService = compoundActivityDefinitionService;
    }

    @Autowired
    final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }

    /** Schema service, used to resolve schema revision numbers for schema references in activities. */
    @Autowired
    final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Autowired
    final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    @Autowired
    final void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(String healthCode,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey,
            int pageSize) {
        checkArgument(isNotBlank(healthCode));

        // ActivityType.SURVEY is a placeholder vald to pass validation, it's not used in this version of the API
        RangeTuple<DateTime> dateRange = validateHistoryParameters(ActivityType.SURVEY, pageSize, scheduledOnStart, scheduledOnEnd);
        
        return activityDao.getActivityHistoryV2(healthCode, activityGuid, dateRange.getStart(), dateRange.getEnd(),
                offsetKey, pageSize);
    }
    
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(String healthCode, ActivityType activityType,
            String referentGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize) {
        checkArgument(isNotBlank(healthCode));
        checkArgument(isNotBlank(referentGuid));
        
        RangeTuple<DateTime> dateRange = validateHistoryParameters(activityType, pageSize, scheduledOnStart, scheduledOnEnd);

        return activityDao.getActivityHistoryV3(healthCode, activityType, referentGuid, dateRange.getStart(),
                dateRange.getEnd(), offsetKey, pageSize);
    }
    
    protected RangeTuple<DateTime> validateHistoryParameters(ActivityType activityType, int pageSize, DateTime scheduledOnStart,
            DateTime scheduledOnEnd) {

        if (activityType == null) {
            throw new BadRequestException("Invalid activity type: " + activityType);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        // If nothing is provided, we will default to two weeks, going max days into future.
        if (scheduledOnStart == null && scheduledOnEnd == null) {
            DateTime now = getDateTime();
            scheduledOnEnd = now.plusDays(MAX_DATE_RANGE_IN_DAYS/2);
            scheduledOnStart = now.minusDays(MAX_DATE_RANGE_IN_DAYS/2);
        }
        // But if only one was provided... we don't know what to do with this. Return bad request exception
        if (scheduledOnStart == null || scheduledOnEnd == null) {
            throw new BadRequestException(EITHER_BOTH_DATES_OR_NEITHER);
        }
        if (scheduledOnStart.isAfter(scheduledOnEnd)) {
            throw new BadRequestException(INVALID_TIME_RANGE);
        }
        return new RangeTuple<>(scheduledOnStart, scheduledOnEnd);
    }
    
    // This needs to be exposed for tests because although we can fix a point of time for tests, we cannot
    // change the time zone in a test without controlling construction of the DateTime instance.
    protected DateTime getDateTime() {
        return DateTime.now();
    }

    public List<ScheduledActivity> getScheduledActivities(Study study, ScheduleContext context) {
        checkNotNull(study);
        checkNotNull(context);
        
        Validate.nonEntityThrowingException(VALIDATOR, context);

        String healthCode = context.getCriteriaContext().getHealthCode();
        activityEventService.publishActivitiesRetrieved(study, healthCode, DateUtils.getCurrentDateTime());
        
        // Add events for scheduling
        Map<String, DateTime> events = createEventsMap(context);
        ScheduleContext updatedContext = new ScheduleContext.Builder().withContext(context).withEvents(events).build();
        
        List<ScheduledActivity> scheduledActivities = scheduleActivitiesForPlans(updatedContext);
        List<ScheduledActivity> dbActivities = activityDao.getActivities(context.getEndsOn().getZone(), scheduledActivities);
        
        // Must be a mutable map because performMerge removes items when found 
        Map<String, ScheduledActivity> dbMap = Maps.newHashMap();
        for (ScheduledActivity dbActivity : dbActivities) {
            dbMap.put(dbActivity.getGuid(), dbActivity);
        }
        
        List<ScheduledActivity> saves = performMerge(scheduledActivities, dbMap);
        activityDao.saveActivities(saves);
        
        return orderActivities(scheduledActivities, V3_FILTER);
    }
    
    public List<ScheduledActivity> getScheduledActivitiesV4(Study study, ScheduleContext context) {
        checkNotNull(study);
        checkNotNull(context);
        
        Validate.nonEntityThrowingException(VALIDATOR, context);
        
        String healthCode = context.getCriteriaContext().getHealthCode();
        activityEventService.publishActivitiesRetrieved(study, healthCode, DateUtils.getCurrentDateTime());
        
        // Add events for scheduling
        Map<String, DateTime> events = createEventsMap(context);
        ScheduleContext updatedContext = new ScheduleContext.Builder().withContext(context).withEvents(events).build();

        List<ScheduledActivity> scheduledActivities = scheduleActivitiesForPlans(updatedContext);

        // Get all persisted activities within the time frame, not just those found by the scheduler (as in v3).
        Map<String, ScheduledActivity> dbMap = retrieveAllPersistedActivitiesIntoMap(updatedContext, scheduledActivities);
        
        // Compare scheduled and persisted activities, replacing scheduled with persisted where they exist
        List<ScheduledActivity> saves = performMerge(scheduledActivities, dbMap);
        activityDao.saveActivities(saves);
        
        // We've removed all the persisted activities that were found by the scheduler. Those have been saved
        // as necessary. The remaining tasks were scheduled based on user-triggered events that can be updated, 
        // not events like "enrollment" but events like "when this activity is finished." These are now all 
        // added to the activities that will be returned.
        scheduledActivities.addAll(dbMap.values());
        
        return orderActivities(scheduledActivities, V4_FILTER);
    }
    
    protected List<ScheduledActivity> performMerge(List<ScheduledActivity> scheduledActivities,
            Map<String, ScheduledActivity> dbMap) {
        List<ScheduledActivity> saves = Lists.newArrayList();
        for (int i=0; i < scheduledActivities.size(); i++) {
            ScheduledActivity activity = scheduledActivities.get(i);
            ScheduledActivity dbActivity = dbMap.remove(activity.getGuid());
            
            if (dbActivity != null && (!UPDATABLE_STATUSES.contains(dbActivity.getStatus()) || dbActivity.getClientData() != null)) {
                // Once the activity is in the database and is in a non-updatable state (and that includes attaching
                // client data to the activity), we should use the one from the database. Otherwise, either (a) it
                // doesn't exist yet and needs to be persisted or (b) the user hasn't interacted with it yet, so we 
                // can safely replace it with the newly generated one, which may have updated schemas or surveys.
                //
                // Note that this only works because the scheduled activity guid is actually the schedule plan's
                // activity guid concatenated with scheduled time. So when the scheduler regenerates the scheduled
                // activity, it always has the same guid.
                scheduledActivities.set(i, dbActivity);
            } else if (activity.getStatus() != ScheduledActivityStatus.EXPIRED) {
                saves.add(activity);
            }
        }
        return saves;
    }
    
    private Map<String, ScheduledActivity> retrieveAllPersistedActivitiesIntoMap(ScheduleContext context,
            List<ScheduledActivity> scheduledActivities) {
        
        Set<String> activityGuids = scheduledActivities.stream().map((activity) -> {
            return activity.getGuid().split(":")[0];
        }).collect(Collectors.toSet());
        
        Map<String,ScheduledActivity> dbMap = Maps.newHashMap();
        // IA-545: If a schedule has an identical activity but a new GUID (say if we change the schedule on the user), the user can 
        // lose existing activities. So during the time window the user is looking at, we will return any activities that exist.
        for (String activityGuid : activityGuids) {
            ForwardCursorPagedResourceList<ScheduledActivity> list = activityDao.getActivityHistoryV2(
                    context.getCriteriaContext().getHealthCode(), activityGuid,
                    context.getStartsOn(), context.getEndsOn(), null,
                    API_MAXIMUM_PAGE_SIZE);
            if (list != null) {
                for(ScheduledActivity activity : list.getItems()) {
                    dbMap.put(activity.getGuid(), activity);
                }
            }
        }
        // IA-587: When a one-time task falls outside the schedule window, it's not returned by the 
        // query above, so it is recreated, and it loses its finished state. Load all remaining scheduled activities.
        String healthCode = context.getCriteriaContext().getHealthCode();
        for (ScheduledActivity activity : scheduledActivities) {
            if (!dbMap.containsKey(activity.getGuid())) {
                ScheduledActivity dbActivity = activityDao.getActivity(context.getStartsOn().getZone(), healthCode,
                        activity.getGuid(), false);
                if (dbActivity != null) {
                    dbMap.put(dbActivity.getGuid(), dbActivity);    
                }
            }
        }
        return dbMap;
    }

    public void updateScheduledActivities(String healthCode, List<ScheduledActivity> scheduledActivities) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(scheduledActivities);
        
        // Remove duplicates sent by the client because these lead to an error when persisting the records
        // (BRIDGE-2350). Preserve the order the activities were submitted in the list, mostly because tests 
        // expect that order to be preserved.
        Set<String> activitiesAlreadySeen = new HashSet<>();
        List<ScheduledActivity> activitiesToSave = new LinkedList<>();
        
        // According to the client team, the last activity is most likely to be correct, so iterate from 
        // the last one
        for (int i=scheduledActivities.size()-1; i >= 0; i--) {
            ScheduledActivity schActivity = scheduledActivities.get(i);
            if (schActivity == null) {
                throw new BadRequestException("A task in the array is null");
            }
            if (schActivity.getGuid() == null) {
                throw new BadRequestException(String.format("Task #%s has no GUID", i));
            }
            if (byteLength(schActivity.getClientData()) > CLIENT_DATA_MAX_BYTES) {
                throw new BadRequestException("Client data too large ("+CLIENT_DATA_MAX_BYTES+" bytes limit) for task "
                        + schActivity.getGuid());
            }

            // This isn't returned to the client, so the exact time zone used does not matter.
            ScheduledActivity dbActivity = activityDao.getActivity(DateTimeZone.UTC, healthCode, schActivity.getGuid(), true);
            String key = dbActivity.getHealthCode()+":"+dbActivity.getGuid();
            if (activitiesAlreadySeen.contains(key)) {
                ScheduledActivity previouslyAdded = activitiesToSave.stream().filter((sch) -> {
                    return sch.getHealthCode().equals(dbActivity.getHealthCode()) && 
                            sch.getGuid().equals(dbActivity.getGuid()); 
                }).findFirst().get();
                LOG.warn("Duplicate activities submitted to server, activity to persist: " + previouslyAdded + ", duplicate: " + dbActivity);
                continue;
            }
            activitiesAlreadySeen.add(key);
            
            boolean addToSaves = false;
            if (hasUpdatedClientData(schActivity, dbActivity)) {
                dbActivity.setClientData(schActivity.getClientData());
                addToSaves = true;
            }
            if (schActivity.getStartedOn() != null) {
                dbActivity.setStartedOn(schActivity.getStartedOn());
                addToSaves = true;
            }
            if (schActivity.getFinishedOn() != null) {
                dbActivity.setFinishedOn(schActivity.getFinishedOn());
                activityEventService.publishActivityFinishedEvent(dbActivity);
                addToSaves = true;
            }
            if (addToSaves) {
                activitiesToSave.add(0, dbActivity);
            }
            
        }
        activityDao.updateActivities(healthCode, activitiesToSave);
    }

    public void deleteActivitiesForUser(String healthCode) {
        checkArgument(isNotBlank(healthCode));

        activityDao.deleteActivitiesForUser(healthCode);
    }

    protected List<ScheduledActivity> orderActivities(List<ScheduledActivity> activities,
            Predicate<ScheduledActivity> filter) {
        return activities.stream()
            .filter(filter)
            .sorted(comparing(ScheduledActivity::getScheduledOn))
            .collect(toImmutableList());
    }

    /**
     * If the client data is being added or removed, or if it is different, then the activity is being
     * updated.
     */
    protected boolean hasUpdatedClientData(ScheduledActivity schActivity, ScheduledActivity dbActivity) {
        JsonNode schNode = (schActivity == null) ? null : schActivity.getClientData();
        JsonNode dbNode = (dbActivity == null) ? null : dbActivity.getClientData();
        return !Objects.equals(schNode, dbNode);
    }

    private int byteLength(JsonNode node) {
        try {
            return (node == null) ? 0 : node.toString().getBytes("UTF-8").length;
        } catch(UnsupportedEncodingException e) {
            return Integer.MAX_VALUE; // UTF-8 is always supported, this should *never* happen
        }
    }

    private Map<String, DateTime> createEventsMap(ScheduleContext context) {
        Map<String,DateTime> events = activityEventService.getActivityEventMap(context.getCriteriaContext().getHealthCode());

        ImmutableMap.Builder<String,DateTime> builder = new ImmutableMap.Builder<String, DateTime>();
        if (!events.containsKey(ENROLLMENT)) {
            builder.put(ENROLLMENT, context.getAccountCreatedOn().withZone(context.getInitialTimeZone()));
        }
        for(Map.Entry<String, DateTime> entry : events.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().withZone(context.getInitialTimeZone()));
        }
        return builder.build();
    }

    protected List<ScheduledActivity> scheduleActivitiesForPlans(ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = new ArrayList<>();

        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(context.getCriteriaContext().getClientInfo(),
                context.getCriteriaContext().getStudyIdentifier(), false);
        
        AppConfig appConfig = appConfigService.getAppConfigForUser(context.getCriteriaContext(), false);
        Map<String, SurveyReference> surveyReferences = (appConfig == null) ? ImmutableMap.of()
                : Maps.uniqueIndex(appConfig.getSurveyReferences(), SurveyReference::getGuid);
        Map<String, SchemaReference> schemaReferences = (appConfig == null) ? ImmutableMap.of()
                : Maps.uniqueIndex(appConfig.getSchemaReferences(), SchemaReference::getId);

        ReferenceResolver resolver = new ReferenceResolver(compoundActivityDefinitionService, schemaService,
                surveyService, surveyReferences, schemaReferences, context.getCriteriaContext().getClientInfo(),
                context.getCriteriaContext().getStudyIdentifier());
        
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            if (schedule != null) {
                List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, context);
                for (ScheduledActivity schActivity : activities) {
                    resolver.resolve(schActivity);
                }
                scheduledActivities.addAll(activities);
            }
        }
        return scheduledActivities;
    }

}
