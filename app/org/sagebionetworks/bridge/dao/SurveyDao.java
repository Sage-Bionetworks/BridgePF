package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(Survey survey);
    
    public Survey publishSurvey(Survey survey);
    
    public List<Survey> getSurveys(String studyKey);
    
    public List<Survey> getSurveys(String studyKey, String surveyGuid);
    
    public void deleteSurvey(Survey survey);

    public void closeSurvey(Survey survey);
    
    /**
     * Get the most recently published survey, if the survey has been published.
     * Otherwise you must use the versioned date to retrieve the survey.
     * @param studyKey
     * @param surveyGuid
     * @return
     */
    public Survey getPublishedSurvey(String studyKey, String surveyGuid);
    
    /**
     * Get a particular survey by version, regardless of publication state.
     * @param studyKey
     * @param surveyGuid
     * @param versionedOn
     * @return
     */
    public Survey getSurvey(String studyKey, String surveyGuid, long versionedOn);

}
