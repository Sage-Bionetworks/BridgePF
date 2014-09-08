package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyService {

    public List<Survey> getSurveys(Study study);
    
    /**
     * Gets all published versions of all surveys. If a survey has not been published, 
     * it is not included in this list.
     * @return
     */
    public List<Survey> getMostRecentlyPublishedSurveys(Study study);
    
    /**
     * Gets the most recent version of all surveys, whether published or not. 
     * @return
     */
    public List<Survey> getMostRecentSurveys(Study study);
    
    /**
     * Get the entire history of versions for one survey, sorted from most to least recently 
     * issued.
     * @param caller
     * @param surveyGuid
     * @return
     */
    public List<Survey> getAllVersionsOfSurvey(String surveyGuid);
    
    /**
     * Get one instance of a survey. This call alone does not require the study's researcher role.
     */
    public Survey getSurvey(String surveyGuid, long versionedOn);
    
    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey publishSurvey(String surveyGuid, long versionedOn);
    
    public Survey closeSurvey(String surveyGuid, long versionedOn);
    
    public Survey versionSurvey(String surveyGuid, long versionedOn);
    
}