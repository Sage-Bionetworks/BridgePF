package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.SurveyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class SurveyServiceImpl implements SurveyService {

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

    @Override
    public Survey getSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.getSurvey(keys);
    }

    @Override
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey, "Survey cannot be null");
        checkNewEntity(survey, survey.getGuid(), "Survey has a GUID; it may already exist");
        checkNewEntity(survey, survey.getVersion(), "Survey has a version value; it may already exist");
        
        survey.setGuid(BridgeUtils.generateGuid());
        Validate.entityThrowingException(validator, survey);    
        return surveyDao.createSurvey(survey);
    }

    @Override
    public Survey updateSurvey(Survey survey) {
        checkNotNull(survey, "Survey cannot be null");
        
        Validate.entityThrowingException(validator, survey);
        return surveyDao.updateSurvey(survey);
    }

    @Override
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.publishSurvey(study, keys);
    }
    
    @Override
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.versionSurvey(keys);
    }
    
    @Override
    public void deleteSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurvey(keys);
    }

    @Override
    public void deleteSurveyPermanently(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurveyPermanently(keys);
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyAllVersions(studyIdentifier, guid);
    }

    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentVersion(studyIdentifier, guid);
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, guid);
    }

    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
    }

    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
    }

    @Override
    public List<Survey> getSurveysSummary(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        
        return surveyDao.getSurveysSummary(studyIdentifier);
    }
    
}