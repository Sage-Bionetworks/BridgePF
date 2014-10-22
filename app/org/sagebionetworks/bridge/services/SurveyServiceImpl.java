package org.sagebionetworks.bridge.services;

import java.util.List;

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
        return surveyDao.getSurveys(study.getKey());
    }
    
    @Override
    public List<Survey> getMostRecentlyPublishedSurveys(Study study) {
        return surveyDao.getMostRecentlyPublishedSurveys(study.getKey());
    }

    @Override
    public List<Survey> getAllVersionsOfSurvey(String surveyGuid) {
        return surveyDao.getSurveyVersions(surveyGuid);
    }

    @Override
    public List<Survey> getMostRecentSurveys(Study study) {
        return surveyDao.getMostRecentSurveys(study.getKey());
    }

    @Override
    public Survey getSurvey(String surveyGuid, long versionedOn) {
        return surveyDao.getSurvey(surveyGuid, versionedOn);
    }

    @Override
    public Survey createSurvey(Survey survey) {
        Validate.entityThrowingException(validator, survey);    
        return surveyDao.createSurvey(survey);
    }

    @Override
    public Survey updateSurvey(Survey survey) {
        Validate.entityThrowingException(validator, survey);
        return surveyDao.updateSurvey(survey);
    }

    @Override
    public Survey publishSurvey(String surveyGuid, long versionedOn) {
        return surveyDao.publishSurvey(surveyGuid, versionedOn);
    }

    @Override
    public Survey closeSurvey(String surveyGuid, long versionedOn) {
        return surveyDao.closeSurvey(surveyGuid, versionedOn);
    }
    
    @Override
    public Survey versionSurvey(String surveyGuid, long versionedOn) {
        return surveyDao.versionSurvey(surveyGuid, versionedOn);
    }
    
    @Override
    public void deleteSurvey(String surveyGuid, long versionedOn) {
        surveyDao.deleteSurvey(surveyGuid, versionedOn);
    }
}