package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.validators.SurveyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class SurveyService {

    private Validator validator;
    private SurveyDao surveyDao;
    
    @Autowired
    public void setSurveyDao(SurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    @Autowired
    public void setValidator(SurveyValidator validator) {
        this.validator = validator;
    }

    /**
     * Get a list of all published surveys in this study, using the most
     * recently published version of each survey. These surveys will include
     * questions (not other element types, such as info screens). Most
     * properties beyond identifiers will be removed from these surveys as they
     * are returned in the API.
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
        survey.setVersion(null);
        for (SurveyElement element : survey.getElements()) {
            element.setGuid(BridgeUtils.generateGuid());
        }

        Validate.entityThrowingException(validator, survey);
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
        
        Validate.entityThrowingException(validator, survey);
        return surveyDao.updateSurvey(survey);
    }

    /**
     * Make this version of this survey available for scheduling. One scheduled
     * for publishing, a survey version can no longer be changed (it can still
     * be the source of a new version). There can be more than one published
     * version of a survey.
     * 
     * @param study
     *            study ID of study to publish the survey to
     * @param keys
     *            survey keys (guid, created on timestamp)
     * @param newSchemaRev
     *         true if you want to cut a new survey schema, false if you should (attempt to) modify the existing one
     * @return published survey
     */
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys, boolean newSchemaRev) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.publishSurvey(study, keys, newSchemaRev);
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
     * Delete this survey. Survey still exists in system and can be retrieved by
     * direct reference (URLs that directly reference the GUID and createdOn
     * timestamp of the survey), put cannot be retrieved in any list of surveys,
     * and is no longer considered when finding the most recently published
     * version of the survey.
     * 
     * @param keys
     */
    public void deleteSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurvey(keys);
    }

    /**
     * Admin API to remove the survey from the backing store. This exists to
     * clean up surveys from tests. This will remove the survey regardless of
     * publish status, whether it has responses. This will delete all survey
     * elements as well.
     *
     * @param keys
     *            survey keys (guid, created-on timestamp)
     */
    public void deleteSurveyPermanently(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurveyPermanently(keys);
    }

    /**
     * Get all versions of a specific survey, ordered by most recent version
     * first in the list.
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
     * Get the most recent version of a survey, regardless of whether it is
     * published or not.
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
     * Get the most recent version of a survey that is published. More recent,
     * unpublished versions of the survey will be ignored.
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
     * Get the most recent version of each survey in the study that has been
     * published. If a survey has not been published, nothing is returned.
     * 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
    }

    /**
     * Get the most recent version of each survey in the study, whether
     * published or not.
     * 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
    }
    
}