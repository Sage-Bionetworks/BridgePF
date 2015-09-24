package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    /**
     * Create a new survey. 
     * @param survey
     * @return
     */
    public Survey createSurvey(Survey survey);
    
    /**
     * Update an unpublished survey. A survey version can be edited until it is published.
     * @param survey
     * @return
     */
    public Survey updateSurvey(Survey survey);
    
    /**
     * Version this survey (create a copy with a new createdOn timestamp). New versions are 
     * created unpublished and can be modified.
     * @param keys
     * @return
     */
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys);

    /**
     * Make this version of this survey available for scheduling. One scheduled for publishing, 
     * a survey version can no longer be changed (it can still be the source of a new version).  
     * There can be more than one published version of a survey.
     * @param keys
     * @return
     */
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys);

    /**
     * Delete this survey. Survey still exists in system and can be retrieved by direct reference
     * (URLs that directly reference the GUID and createdOn timestamp of the survey), put cannot be 
     * retrieved in any list of surveys, and is no longer considered when finding the most recently 
     * published version of the survey. 
     *  
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
     * Get a specific version of a survey.
     * @param keys
     * @return
     */
    public Survey getSurvey(GuidCreatedOnVersionHolder keys);
    
    /**
     * Get all versions of a specific survey, ordered by most recent version 
     * first in the list.
     * @param studyIdentifier
     * @param guid
     * @return
     */
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid);    
    
    /**
     * Get the most recent version of a survey, regardless of whether it is published
     * or not.
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
     * Get the most recent version of each survey in the study, that has been published. 
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
     * These surveys will include questions with their identifiers.  
     * @param studyIdentifier
     * @return
     */
    public List<Survey> getSurveysSummary(StudyIdentifier studyIdentifier);
    
}
