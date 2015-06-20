package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys);
    
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys);
    
    /**
     * Delete a survey. If a survey is published, or if there is a schedule plan
     * that references the survey or a survey response based on the survey, then 
     * the survey cannot be deleted. The survey responses and schedule plans must 
     * first be deleted, and the survey closed (unpublished), before the survey 
     * can be deleted.
     *  
     * @param studyIdentifier
     * @param keys
     */
    public void deleteSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys);
    
    /**
     * Unpublish the survey, closing out any active records that are still 
     * pointing to this survey. 
     * @param keys
     * @return
     */
    public Survey closeSurvey(GuidCreatedOnVersionHolder keys);

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
     * Get the most recently published version of a survey using its identifier.
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
    
}
