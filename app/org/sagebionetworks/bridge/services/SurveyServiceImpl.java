package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

/**
 * NOTE: This has become just a pass-through between layers, shall we just 
 * call the DAO from the controller?
 */
public class SurveyServiceImpl implements SurveyService {
    
    private SurveyDao surveyDao;
    
    public void setSurveyDao(SurveyDao surveyDao) {
        this.surveyDao = surveyDao;
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
        return surveyDao.createSurvey(survey);
    }

    @Override
    public Survey updateSurvey(Survey survey) {
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

}