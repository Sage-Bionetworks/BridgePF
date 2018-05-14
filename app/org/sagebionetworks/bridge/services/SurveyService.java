package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HqlWhereClause;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.validators.SurveyPublishValidator;
import org.sagebionetworks.bridge.validators.SurveySaveValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class SurveyService {

    private Validator publishValidator;
    private SurveyDao surveyDao;
    private SchedulePlanService schedulePlanService;
    private SharedModuleMetadataService sharedModuleMetadataService;
    private StudyService studyService;

    @Autowired
    final void setSurveyDao(SurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }

    @Autowired
    final void setPublishValidator(SurveyPublishValidator validator) {
        this.publishValidator = validator;
    }

    @Autowired
    final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }

    @Autowired
    public final void setSharedModuleMetadataService(SharedModuleMetadataService sharedModuleMetadataService) {
        this.sharedModuleMetadataService = sharedModuleMetadataService;
    }

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    /**
     * Get a list of all published surveys in this study, using the most recently published version of each survey.
     * These surveys will include questions (not other element types, such as info screens). Most properties beyond
     * identifiers will be removed from these surveys as they are returned in the API.
     *
     * @param studyIdentifier
     * @return
     */
    public Survey getSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        return surveyDao.getSurvey(keys);
    }

    /**
     * Create a survey.
     * 
     * @param survey
     * @return
     */
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey, "Survey cannot be null");

        survey.setGuid(BridgeUtils.generateGuid());
        for (SurveyElement element : survey.getElements()) {
            element.setGuid(BridgeUtils.generateGuid());
        }
        Set<String> dataGroups = Collections.emptySet();
        if (survey.getStudyIdentifier() != null) {
            Study study = studyService.getStudy(survey.getStudyIdentifier());
            dataGroups = study.getDataGroups();
        }
        Validate.entityThrowingException(new SurveySaveValidator(dataGroups), survey);
        
        return surveyDao.createSurvey(survey);
    }

    /**
     * Update an existing survey.
     * 
     * @param survey
     * @return
     */
    public Survey updateSurvey(Survey survey) {
        checkNotNull(survey, "Survey cannot be null");
        
        Set<String> dataGroups = Collections.emptySet();
        if (survey.getStudyIdentifier() != null) {
            Study study = studyService.getStudy(survey.getStudyIdentifier());
            dataGroups = study.getDataGroups();
        }
        Validate.entityThrowingException(new SurveySaveValidator(dataGroups), survey);
        
        return surveyDao.updateSurvey(survey);
    }

    /**
     * Make this version of this survey available for scheduling. One scheduled for publishing, a survey version can no
     * longer be changed (it can still be the source of a new version). There can be more than one published version of
     * a survey.
     * 
     * @param study
     *            study ID of study to publish the survey to
     * @param keys
     *            survey keys (guid, created on timestamp)
     * @param newSchemaRev
     *            true if you want to cut a new survey schema, false if you should (attempt to) modify the existing one
     * @return published survey
     */
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys, boolean newSchemaRev) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        Survey survey = surveyDao.getSurvey(keys);
        Validate.entityThrowingException(publishValidator, survey);

        return surveyDao.publishSurvey(study, survey, keys, newSchemaRev);
    }

    /**
     * Copy the survey and return a new version of it.
     * 
     * @param keys
     * @return
     */
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        return surveyDao.versionSurvey(keys);
    }

    /**
     * Logically delete this survey (mark it deleted and do not return it from any list-based APIs; continue 
     * to provide it when the version is specifically referenced). Once a survey is published, you cannot 
     * delete it, because we do not know if it has already been dereferenced in scheduled activities. Any 
     * survey version that could have been sent to users will remain in the API so you can look at its 
     * schema, etc. This is how study developers should delete surveys. 
     */
    public void deleteSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        Survey existing = surveyDao.getSurvey(keys);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Survey.class);
        }

        // verify if a shared module refers to it
        verifySharedModuleExistence(keys);

        surveyDao.deleteSurvey(existing);
    }

    /**
     * <p>Physically remove the survey from the database. This API is mostly for test and early development 
     * clean up, so it ignores the publication flag, however, we do enforce some constraints:</p>
     * <ol>
     *  <li>if a schedule references a specific survey version, don't allow it to be deleted. You can't 
     *      make these through the Bridge Study Manager, but they're allowable in the API;</li>
     *      
     *  <li>if a schedule references the most-recently published version of a survey, verify this delete 
     *      is not removing the last published instance of the survey. This is the more common case 
     *      right now.</li>
     * </ol>
     */
    public void deleteSurveyPermanently(StudyIdentifier studyId, GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        checkConstraintsBeforePhysicalDelete(studyId, keys);

        surveyDao.deleteSurveyPermanently(keys);
    }

    // Helper method to verify if there is any shared module related to specified survey
    private void verifySharedModuleExistence(GuidCreatedOnVersionHolder keys) {
        HqlWhereClause clause = new HqlWhereClause(false);
        clause.addExpression("surveyGuid=:surveyGuid", keys.getGuid());
        clause.addExpression("surveyCreatedOn=:surveyCreatedOn", keys.getCreatedOn());
        List<SharedModuleMetadata> sharedModuleMetadataList = sharedModuleMetadataService.queryAllMetadata(false, false,
                clause, null);

        if (sharedModuleMetadataList.size() != 0) {
            throw new BadRequestException("Cannot delete specified survey because a shared module still refers to it.");
        }
    }

    /**
     * Get all versions of a specific survey, ordered by most recent version first in the list.
     * 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyAllVersions(studyIdentifier, guid);
    }

    /**
     * Get the most recent version of a survey, regardless of whether it is published or not.
     * 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentVersion(studyIdentifier, guid);
    }

    /**
     * Get the most recent version of a survey that is published. More recent, unpublished versions of the survey will
     * be ignored.
     * 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, guid);
    }

    /**
     * Get the most recent version of each survey in the study that has been published. If a survey has not been
     * published, nothing is returned.
     * 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
    }

    /**
     * Get the most recent version of each survey in the study, whether published or not.
     * 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
    }
    
    private void checkConstraintsBeforePhysicalDelete(final StudyIdentifier studyId, final GuidCreatedOnVersionHolder keys) {
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyId);

        // If a schedule points to this specific survey, don't allow the physical delete.
        SchedulePlan match = findFirstMatchingPlan(plans, keys, (surveyReference, theseKeys) -> {
            return surveyReference.equalsSurvey(theseKeys);
        });
        if (match != null) {
            throwConstraintViolation(match, keys);
        }

        // If there's a pointer to the published version of this study, make sure this is not the last one.
        match = findFirstMatchingPlan(plans, keys, (surveyReference, theseKeys) -> {
            return surveyReference.getGuid().equals(theseKeys.getGuid());
        });
        if (match != null) {
            long publishedSurveys = getSurveyAllVersions(studyId, keys.getGuid()).stream()
                    .filter(Survey::isPublished).collect(Collectors.counting());

            if (publishedSurveys == 1L) {
                throwConstraintViolation(match, keys);
            }
        }
        // verify shared module existence as well
        verifySharedModuleExistence(keys);
    }

    private void throwConstraintViolation(SchedulePlan match, final GuidCreatedOnVersionHolder keys) {
        // It's a little absurd to provide type=Survey, but in a UI that's orchestrating
        // several calls, it might not be obvious.
        throw new ConstraintViolationException.Builder().withEntityKey("guid", keys.getGuid())
                .withEntityKey("createdOn", DateUtils.convertToISODateTime(keys.getCreatedOn()))
                .withEntityKey("type", "Survey").withReferrerKey("guid", match.getGuid())
                .withReferrerKey("type", "SchedulePlan").build();
    }

    private SchedulePlan findFirstMatchingPlan(List<SchedulePlan> plans, GuidCreatedOnVersionHolder keys,
            BiPredicate<SurveyReference, GuidCreatedOnVersionHolder> predicate) {
        for (SchedulePlan plan : plans) {
            List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
            for (Schedule schedule : schedules) {
                for (Activity activity : schedule.getActivities()) {
                    if (activity.getSurvey() != null) {
                        if (predicate.test(activity.getSurvey(), keys)) {
                            return plan;
                        }
                    } else if (activity.getCompoundActivity() != null) {
                        CompoundActivity compoundActivity = activity.getCompoundActivity();
                        for (SurveyReference aSurveyRef : compoundActivity.getSurveyList()) {
                            
                            if (predicate.test(aSurveyRef, keys)) {
                                return plan;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

}