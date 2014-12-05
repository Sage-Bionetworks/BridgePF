package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(String surveyGuid, long createdOn);
    
    public Survey publishSurvey(String surveyGuid, long createdOn);
    
    public List<Survey> getSurveys(String studyKey);
    
    /**
     * Get the most recently published version of each survey that has been 
     * published. These are the survey instances that would be shown to a 
     * researcher when creating schedule plans.
     * 
     * @param studyKey
     * @return a list of surveys, each with a different guid, where is is the most
     *  recently published instance of a survey.
     */
    public List<Survey> getMostRecentlyPublishedSurveys(String studyKey);
    
    /**
     * Get the most recent version of each survey in the study, whether 
     * published or not.
     * @param studyKey
     * @return a list of surveys, each with a different guid, each of which is the 
     * most recent instance of that survey.
     */
    public List<Survey> getMostRecentSurveys(String studyKey);    
    
    /**
     * Get all versions of a specific survey, published or not.
     * @param surveyGuid
     * @return
     */
    public List<Survey> getSurveyVersions(String surveyGuid);
    
    /**
     * Delete a survey. A survey cannot be deleted if it has been published. 
     * You must first close the survey, which will address any links to the 
     * survey before it is unpublished; then it can be deleted.
     * 
     * NOTE: If there are any references to this survey (survey responses or 
     * survey plans that schedule the survey), then it may not be deleted. 
     * It may be necessary to delete both kinds of entities before this 
     * method will work. Generally this method will only be used by tests.
     *  
     * @param study
     * @param surveyGuid
     * @param createdOn
     */
    public void deleteSurvey(Study study, String surveyGuid, long createdOn);

    /**
     * Unpublish the survey, closing out any active records that are still 
     * pointing to this survey. 
     * @param surveyGuid
     * @param createdOn
     * @return
     */
    public Survey closeSurvey(String surveyGuid, long createdOn);
    
    
    /**
     * Get a particular survey by version, regardless of publication state.
     * @param surveyGuid
     * @param createdOn
     * @return
     */
    public Survey getSurvey(String surveyGuid, long createdOn);
    
}
