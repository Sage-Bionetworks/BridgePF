package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

public class SurveyServiceImpl implements SurveyService {

    private Validator validator;
    
    private SurveyDao surveyDao;
    
    public void setSurveyDao(SurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    public void setValidator(Validator validator) {
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
    public Survey publishSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.publishSurvey(keys);
    }

    @Override
    public Survey closeSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.closeSurvey(keys);
    }
    
    @Override
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys) {
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.versionSurvey(keys);
    }
    
    @Override
    public void deleteSurvey(Study study, GuidCreatedOnVersionHolder keys) {
        checkNotNull(study, "study cannot be null");
        checkArgument(StringUtils.isNotBlank(keys.getGuid()), "Survey GUID cannot be null/blank");
        checkArgument(keys.getCreatedOn() != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurvey(study, keys);
    }

    @Override
    public List<Survey> getSurveyAllVersions(Study study, String guid) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyAllVersions(study.getIdentifier(), guid);
    }

    @Override
    public Survey getSurveyMostRecentVersion(Study study, String guid) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentVersion(study.getIdentifier(), guid);
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(Study study, String guid) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "survey guid");

        return surveyDao.getSurveyMostRecentlyPublishedVersion(study.getIdentifier(), guid);
    }

    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentlyPublishedVersion(study.getIdentifier());
    }

    @Override
    public List<Survey> getAllSurveysMostRecentVersion(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        return surveyDao.getAllSurveysMostRecentVersion(study.getIdentifier());
    }

}