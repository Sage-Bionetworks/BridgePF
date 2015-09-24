package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyService {

    /**
     * Get all versions of a specific survey, ordered by most recent version first in the list.
     * 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid);    
    
    /**
     * Get the most recent version of a survey, regardless of whether it is published or not.
     * 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid);
    
    /**
     * Get the most recent version of a survey that is published. More recent, unpublished 
     * versions of the survey will be ignored. 
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid);
    
    /**
     * Get the most recent version of each survey in the study that has been published. If a survey has not 
     * been published, nothing is returned. 
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier);
    
    /**
     * Get the most recent version of each survey in the study, whether published or not.
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier);
    
    /**
     * Get a list of all surveys published in this survey, using the most recent published version. 
     * These surveys will include questions with their identifiers (not other type of elements, like 
     * info screens). This is a slower call, and we only expose it in the API with most JSON properties 
     * removed (all but identifiers).
     *   
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getSurveysSummary(StudyIdentifier studyIdentifier);
    
    /**
     * Get one instance of a survey. This method will return a survey regardless of whether or not it has been 
     * published. It will return a survey even if it has been deleted.
     * @param keys
     * @return
     */
    public Survey getSurvey(GuidCreatedOnVersionHolder keys);
    
    /**
     * Create a survey.
     * @param survey
     * @return
     */
    public Survey createSurvey(Survey survey);
    
    /**
     * Update an existing survey.
     * @param survey
     * @return
     */
    public Survey updateSurvey(Survey survey);

    /**
     * Make this version of this survey available for scheduling. One scheduled for publishing,
     * a survey version can no longer be changed (it can still be the source of a new version).  
     * There can be more than one published version of a survey.
     *  
     * @param study
     *         study ID of study to publish the survey to
     * @param keys
     *         survey keys (guid, created on timestamp)
     * @return published survey
     */
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys);

    /**
     * Delete this survey. Survey still exists in system and can be retrieved by direct reference
     * (URLs that directly reference the GUID and createdOn timestamp of the survey), put cannot be 
     * retrieved in any list of surveys, and is no longer considered when finding the most recently 
     * published version of the survey. 
     * @param keys
     */
    public void deleteSurvey(GuidCreatedOnVersionHolder keys);

    /**
     * Admin API to remove the survey from the backing store. This exists to clean up surveys from tests. This will
     * remove the survey regardless of publish status, whether it has responses. This will delete all survey elements
     * as well.
     *
     * @param keys survey keys (guid, created-on timestamp)
     */
    public void deleteSurveyPermanently(GuidCreatedOnVersionHolder keys);

    /**
     * Copy the survey and return a new version of it.
     * @param keys
     * @return
     */
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys);
    
}