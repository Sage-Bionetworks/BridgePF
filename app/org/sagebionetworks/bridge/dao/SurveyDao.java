package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(String surveyGuid, long versionedOn);
    
    public Survey publishSurvey(String surveyGuid, long versionedOn);
    
    public List<Survey> getSurveys(String studyKey);
    
    /**
     * Get the most recently published version of each survey that has been 
     * published. These are usually the only surveys that a user would want 
     * to use when creating activities (?)
     * @param surveyGuid
     * @return
     */
    public List<Survey> getMostRecentlyPublishedSurveys(String studyKey);
    
    
    public List<Survey> getSurveyVersions(String studyKey, String surveyGuid);
    
    /**
     * Delete a survey. A survey cannot be deleted if it has been published. 
     * You must first close the survey, which will address any links to the 
     * survey before it is unpublished; then it can be deleted.
     * 
     * NOTE: If there are any references to this survey, then in it 
     * may not be deleted. It may be necessary to delete survey responses 
     * before this method will work. Generally this method will only be used 
     * by tests.
     *  
     * @param surveyGuid
     * @param versionedOn
     */
    public void deleteSurvey(String surveyGuid, long versionedOn);

    /**
     * Unpublish the survey, closing out any active records that are still 
     * pointing to this survey. 
     * @param surveyGuid
     * @param versionedOn
     * @return
     */
    public Survey closeSurvey(String surveyGuid, long versionedOn);
    
    
    /**
     * Get a particular survey by version, regardless of publication state.
     * @param surveyGuid
     * @param versionedOn
     * @return
     */
    public Survey getSurvey(String surveyGuid, long versionedOn);

}
