package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class SchedulePlanServiceImpl implements SchedulePlanService {
    
    private SchedulePlanDao schedulePlanDao;
    private SurveyService surveyService;
    private ScheduledActivityService activityService;

    @Autowired
    public final void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    @Autowired
    public final void setScheduledActivityService(ScheduledActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, StudyIdentifier studyIdentifier) {
        return schedulePlanDao.getSchedulePlans(clientInfo, studyIdentifier);
    }

    @Override
    public SchedulePlan getSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        return schedulePlanDao.getSchedulePlan(studyIdentifier, guid);
    }

    @Override
    public SchedulePlan createSchedulePlan(Study study, SchedulePlan plan) {
        checkNotNull(study);
        checkNotNull(plan);

        // Plan must always be in user's study
        plan.setStudyKey(study.getIdentifier());

        // Delete existing GUIDs so this is a new object (or recreate them)
        Validate.entityThrowingException(new SchedulePlanValidator(study.getDataGroups(), study.getTaskIdentifiers()), plan);
        updateGuids(plan);

        StudyIdentifier studyId = new StudyIdentifierImpl(plan.getStudyKey());
        lookupSurveyReferenceIdentifiers(studyId, plan);
        return schedulePlanDao.createSchedulePlan(studyId, plan);
    }
    
    @Override
    public SchedulePlan updateSchedulePlan(Study study, SchedulePlan plan) {
        checkNotNull(study);
        checkNotNull(plan);
        
        // Plan must always be in user's study
        plan.setStudyKey(study.getIdentifier());
        
        Validate.entityThrowingException(new SchedulePlanValidator(study.getDataGroups(), study.getTaskIdentifiers()), plan);
        
        StudyIdentifier studyId = new StudyIdentifierImpl(plan.getStudyKey());
        lookupSurveyReferenceIdentifiers(studyId, plan);
        plan = schedulePlanDao.updateSchedulePlan(studyId, plan);
        activityService.deleteActivitiesForSchedulePlan(plan.getGuid());
        return plan;
    }

    @Override
    public void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier);
        checkNotNull(isNotBlank(guid));
        
        schedulePlanDao.deleteSchedulePlan(studyIdentifier, guid);
        
        activityService.deleteActivitiesForSchedulePlan(guid);
    }
    
    /**
     * Saving a new plan that has GUIDs and is an existing plan? Clear them out so that we create a 
     * new copy of the plan.
     * @param plan
     */
    private void updateGuids(SchedulePlan plan) {
        plan.setVersion(null);
        plan.setGuid(BridgeUtils.generateGuid());
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                schedule.getActivities().set(i, new Activity.Builder()
                    .withActivity(activity).withGuid(BridgeUtils.generateGuid()).build());
            }
        }
    }

    /**
     * If the activity has a survey reference, look up the survey's identifier. Don't trust the client to 
     * supply the correct one for the survey's primary keys. We're adding this when writing schedules because 
     * the clients have come to depend on it, and this is more efficient than looking it up on every read.
     * 
     * @param studyId
     * @param activity
     * @return
     */
    private void lookupSurveyReferenceIdentifiers(StudyIdentifier studyId, SchedulePlan plan) {
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                activity = updateActivityWithSurveyIdentifier(studyId, activity);
                schedule.getActivities().set(i, activity);
            }
        }
    }

    private Activity updateActivityWithSurveyIdentifier(StudyIdentifier studyId, Activity activity) {
        if (activity.getSurvey() != null) {
            SurveyReference ref = activity.getSurvey();
            
            if (ref.getCreatedOn() == null) { // pointer to most recently published survey
                Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(studyId, ref.getGuid());
                return new Activity.Builder().withActivity(activity)
                        .withPublishedSurvey(survey.getIdentifier(), survey.getGuid()).build();
            } else {
                GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref.getGuid(), ref.getCreatedOn().getMillis());
                Survey survey = surveyService.getSurvey(keys);
                return new Activity.Builder().withActivity(activity)
                        .withSurvey(survey.getIdentifier(), ref.getGuid(), ref.getCreatedOn()).build();
            }
        }
        return activity;
    }
}
