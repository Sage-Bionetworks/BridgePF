package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
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

import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class SurveyService {
    static final String KEY_IDENTIFIER = "identifier";

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
    
    public Survey getSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys, boolean includeElements, boolean throwException) {
        Survey survey = surveyDao.getSurvey(keys, includeElements);
        if (!isInStudy(studyIdentifier, survey)) {
            if (throwException) {
                throw new EntityNotFoundException(Survey.class);    
            }
            return null;
        }
        return survey;
    }
    
    /**
     * Create a survey.
     */
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey, "Survey cannot be null");

        // Check survey ID uniqueness.
        if (isNotBlank(survey.getStudyIdentifier()) && isNotBlank(survey.getIdentifier())) {
            StudyIdentifier studyId = new StudyIdentifierImpl(survey.getStudyIdentifier());
            String existingSurveyGuid = surveyDao.getSurveyGuidForIdentifier(studyId, survey.getIdentifier());
            if (existingSurveyGuid != null) {
                String errMsg = "Survey identifier " + survey.getIdentifier() + " is already used by survey " +
                        existingSurveyGuid;
                Map<String, Object> entityKeyMap = ImmutableMap.of(KEY_IDENTIFIER, survey.getIdentifier());
                throw new EntityAlreadyExistsException(Survey.class, errMsg, entityKeyMap);
            }
        }

        // Validate survey.
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
     */
    public Survey updateSurvey(StudyIdentifier studyIdentifier, Survey survey) {
        checkNotNull(survey, "Survey cannot be null");
        
        Survey existing = surveyDao.getSurvey(survey, false);
        if (existing == null || (existing.isDeleted() && survey.isDeleted()) || !isInStudy(studyIdentifier, survey)) {
            throw new EntityNotFoundException(Survey.class);
        }

        if (existing.isPublished()) {
            // If the existing survey is published, the only thing you can do is undelete it.
            if (existing.isDeleted() && !survey.isDeleted()) {
                existing = surveyDao.getSurvey(survey, true); // get all the children for the update
                existing.setDeleted(false);
                return surveyDao.updateSurvey(existing);
            } else {
                throw new PublishedSurveyException(survey);
            }
        }
        
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
     */
    public Survey publishSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys, boolean newSchemaRev) {
        Survey existing = surveyDao.getSurvey(keys, true);
        if (existing == null || existing.isDeleted() || !isInStudy(studyIdentifier, existing)) {
            throw new EntityNotFoundException(Survey.class);
        }
        Validate.entityThrowingException(publishValidator, existing);

        return surveyDao.publishSurvey(studyIdentifier, existing, newSchemaRev);
    }

    /**
     * Copy the survey and return a new version of it.
     */
    public Survey versionSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existing = surveyDao.getSurvey(keys, false);
        if (existing == null || existing.isDeleted() || !isInStudy(studyIdentifier, existing)) {
            throw new EntityNotFoundException(Survey.class);
        }
        return surveyDao.versionSurvey(keys);
    }

    /**
     * Logically delete this survey (mark it deleted and do not return it from any list-based APIs; continue 
     * to provide it when the version is specifically referenced). Once a survey is published, you cannot 
     * delete it, because we do not know if it has already been dereferenced in scheduled activities. Any 
     * survey version that could have been sent to users will remain in the API so you can look at its 
     * schema, etc. This is how study developers should delete surveys. 
     */
    public void deleteSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existing = surveyDao.getSurvey(keys, true);
        if (existing == null || existing.isDeleted() || !isInStudy(studyIdentifier, existing)) {
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
    public void deleteSurveyPermanently(StudyIdentifier studyIdentifier,
            GuidCreatedOnVersionHolder keys) {
        Survey existing = surveyDao.getSurvey(keys, false);
        if (existing == null || !isInStudy(studyIdentifier, existing)) {
            throw new EntityNotFoundException(Survey.class);
        }
        checkConstraintsBeforePhysicalDelete(studyIdentifier, keys);
        surveyDao.deleteSurveyPermanently(keys);
    }

    // Helper method to verify if there is any shared module related to specified survey
    private void verifySharedModuleExistence(GuidCreatedOnVersionHolder keys) {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("surveyGuid", keys.getGuid());
        parameters.put("surveyCreatedOn", keys.getCreatedOn());
        
        // We do include deleted shared modules so they are not broken when a survey is deleted
        List<SharedModuleMetadata> sharedModuleMetadataList = sharedModuleMetadataService.queryAllMetadata(false, false,
                "surveyGuid=:surveyGuid AND surveyCreatedOn=:surveyCreatedOn", parameters, null, true);

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
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid, boolean includeDeleted) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        List<Survey> allVersions = surveyDao.getSurveyAllVersions(studyIdentifier, guid, includeDeleted);
        if (allVersions.isEmpty()) {
            throw new EntityNotFoundException(Survey.class);
        }
        return allVersions;
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

        Survey survey = surveyDao.getSurveyMostRecentVersion(studyIdentifier, guid);
        if (survey == null || !isInStudy(studyIdentifier, survey)) {
            throw new EntityNotFoundException(Survey.class);
        }
        return survey;
    }

    /**
     * Get the most recent version of a survey that is published. More recent, unpublished versions of the survey will
     * be ignored.
     * 
     * @param studyIdentifier
     * @param guid
     * @param includeElements
     *      if true, include the child elements, otherwise the collection is empty
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid, boolean includeElements) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        Survey survey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, guid, includeElements);
        if (survey == null || !isInStudy(studyIdentifier, survey)) {
            throw new EntityNotFoundException(Survey.class);
        }
        return survey;
    }

    /**
     * Get the most recent version of each survey in the study that has been published. If a survey has not been
     * published, nothing is returned.
     */
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier, includeDeleted);
    }

    /**
     * Get the most recent version of each survey in the study, whether published or not.
     * 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentVersion(studyIdentifier, includeDeleted);
    }

    /**
     * Callers must operate on a survey in their own study. However our code has allowed administrators to delete 
     * shared studies (which are not in the admin's study). For backwards compatibility, do not enforce the same 
     * study rule for shared study surveys. Eventually we want admins to be able to switch into the shared study in 
     * order to delete items there, then this exception to the check can be removed.
     */
    private boolean isInStudy(StudyIdentifier studyId, Survey survey) {
        if (studyId == null) {
            return true;
        }
        if (survey == null || survey.getStudyIdentifier() == null) {
            return false;
        }
        return survey.getStudyIdentifier().equals(studyId.getIdentifier());
    }
    
    private void checkConstraintsBeforePhysicalDelete(final StudyIdentifier studyId, final GuidCreatedOnVersionHolder keys) {
        // You cannot physically delete a survey if it is referenced by a logically deleted schedule plan. It's possible
        // the schedule plan could be restored. All you can do is logically delete the survey.
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyId, true);

        // If a schedule points to this specific survey, don't allow the physical delete.
        SchedulePlan match = findFirstMatchingPlan(plans, keys, (surveyReference, theseKeys) -> {
            return surveyReference.equalsSurvey(theseKeys);
        });
        if (match != null) {
            throwConstraintViolation(match, keys);
        }

        // If there's a pointer to the published version of this survey, make sure this is not the last published survey
        match = findFirstMatchingPlan(plans, keys, (surveyReference, theseKeys) -> {
            return surveyReference.getGuid().equals(theseKeys.getGuid());
        });
        if (match != null) {
            // A plan points to this survey's published version, so there must be at least one published version
            // that's not logically deleted
            long publishedSurveys = getSurveyAllVersions(studyId, keys.getGuid(), false).stream()
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
        throw new ConstraintViolationException.Builder().withMessage(
                "Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API")
                .withEntityKey("guid", keys.getGuid())
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