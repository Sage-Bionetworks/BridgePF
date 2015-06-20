package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyService {

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
     * Get the most recently published version of a survey, using the identifier for the
     * @param studyIdentifier
     * @param identifier
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersionByIdentifier(StudyIdentifier studyIdentifier, String identifier);
    
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
     * Get one instance of a survey. This call alone does not require the study's researcher role.
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
     * Publish this survey. Although a non-published survey must still be accessible to users (in case
     * a schedule has been cached that references that survey), surveys should not be available for
     * assignment to schedules until they are published.
     *
     * @param study
     *         study ID of study to publish the survey to
     * @param keys
     *         survey keys (guid, created on timestamp)
     * @return published survey
     */
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys);

    /**
     * Delete a survey. A survey cannot be deleted unless 1) it is not published (either never was published 
     * or was subsequently closed); 2) there are no survey responses created against this survey, and 3) 
     * the survey is not referenced in any schedule plans. NOTE that the survey may still be referenced by 
     * schedules that have been issued to clients (due to caching), so this action should still be done with 
     * care. 
     * @param studyIdentifier
     * @param keys
     */
    public void deleteSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys);
    
    /**
     * Un-publish a survey. Survey will still be accessible to any users who have schedules that point to the 
     * survey, but the survey should not be available for further scheduling.
     * @param keys
     * @return
     */
    public Survey closeSurvey(GuidCreatedOnVersionHolder keys);
    
    /**
     * Copy the survey and return a new version of it.
     * @param keys
     * @return
     */
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys);
    
}