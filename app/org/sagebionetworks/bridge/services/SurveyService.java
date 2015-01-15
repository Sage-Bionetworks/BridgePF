package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyService {

    /**
     * Get all versions of a specific survey, ordered by most recent version 
     * first in the list.
     * @param study
     * @param guid
     * @return
     */
    public List<Survey> getSurveyAllVersions(Study study, String guid);    
    
    /**
     * Get the most recent version of a survey, regardless of whether it is published
     * or not.
     * @param study
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentVersion(Study study, String guid);
    
    /**
     * Get the most recent version of a survey that is published. More recent, unpublished 
     * versions of the survey will be ignored. 
     * @param study
     * @param guid
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersion(Study study, String guid);
    
    /**
     * Get the most recently published version of a survey, using the identifier for the 
     * survey.
     * @return
     */
    public Survey getSurveyMostRecentlyPublishedVersionByIdentifier(Study study, String identifier);
    
    /**
     * Get the most recent version of each survey in the study, that has been published. 
     * @param study
     * @return
     */
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(Study study);
    
    /**
     * Get the most recent version of each survey in the study, whether published or not.
     * @param study
     * @return
     */
    public List<Survey> getAllSurveysMostRecentVersion(Study study);
    
    /**
     * Get one instance of a survey. This call alone does not require the study's researcher role.
     */
    public Survey getSurvey(GuidCreatedOnVersionHolder keys);
    
    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey publishSurvey(GuidCreatedOnVersionHolder keys);
    
    public void deleteSurvey(Study study, GuidCreatedOnVersionHolder keys);
    
    public Survey closeSurvey(GuidCreatedOnVersionHolder keys);
    
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys);
    
}