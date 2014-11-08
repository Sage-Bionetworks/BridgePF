package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.Study;
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
    public List<Survey> getSurveys(Study study) {
        checkNotNull(study, "Study cannot be null");
        checkArgument(StringUtils.isNotBlank(study.getKey()), "Study key cannot be blank or null");

        return surveyDao.getSurveys(study.getKey());
    }
    
    @Override
    public List<Survey> getMostRecentlyPublishedSurveys(Study study) {
        checkNotNull(study, "Study cannot be null");
        checkArgument(StringUtils.isNotBlank(study.getKey()), "Study key cannot be blank or null");
        
        return surveyDao.getMostRecentlyPublishedSurveys(study.getKey());
    }

    @Override
    public List<Survey> getAllVersionsOfSurvey(String surveyGuid) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID is required");
        
        return surveyDao.getSurveyVersions(surveyGuid);
    }

    @Override
    public List<Survey> getMostRecentSurveys(Study study) {
        checkNotNull(study, "Study cannot be null");
        checkArgument(StringUtils.isNotBlank(study.getKey()), "Study key cannot be blank or null");
        
        return surveyDao.getMostRecentSurveys(study.getKey());
    }

    @Override
    public Survey getSurvey(String surveyGuid, long createdOn) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID cannot be null/blank");
        checkArgument(createdOn != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.getSurvey(surveyGuid, createdOn);
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
    public Survey publishSurvey(String surveyGuid, long createdOn) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID cannot be null/blank");
        checkArgument(createdOn != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.publishSurvey(surveyGuid, createdOn);
    }

    @Override
    public Survey closeSurvey(String surveyGuid, long createdOn) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID cannot be null/blank");
        checkArgument(createdOn != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.closeSurvey(surveyGuid, createdOn);
    }
    
    @Override
    public Survey versionSurvey(String surveyGuid, long createdOn) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID cannot be null/blank");
        checkArgument(createdOn != 0L, "Survey createdOn timestamp cannot be 0");
        
        return surveyDao.versionSurvey(surveyGuid, createdOn);
    }
    
    @Override
    public void deleteSurvey(String surveyGuid, long createdOn) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey GUID cannot be null/blank");
        checkArgument(createdOn != 0L, "Survey createdOn timestamp cannot be 0");

        surveyDao.deleteSurvey(surveyGuid, createdOn);
    }
}