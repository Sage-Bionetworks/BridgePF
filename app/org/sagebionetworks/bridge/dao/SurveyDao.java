package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(String surveyGuid, long versionedOn);
    
    public Survey publishSurvey(String surveyGuid, long versionedOn);
    
    public List<Survey> getSurveys(String studyKey);
    
    public List<Survey> getSurveyVersions(String studyKey, String surveyGuid);
    
    public void deleteSurvey(String surveyGuid, long versionedOn);

    public Survey closeSurvey(String surveyGuid, long versionedOn);
    
    /**
     * Get the most recently published survey, if the survey has been published.
     * Otherwise you must use the versionedOn date to retrieve the survey.
     * @param surveyGuid
     * @return
     */
    public Survey getPublishedSurvey(String surveyGuid);
    
    /**
     * Get a particular survey by version, regardless of publication state.
     * @param surveyGuid
     * @param versionedOn
     * @return
     */
    public Survey getSurvey(String surveyGuid, long versionedOn);

}
