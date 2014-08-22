package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    public Survey createSurvey(Survey survey);
    
    public Survey updateSurvey(Survey survey);
    
    public Survey versionSurvey(String surveyGuid);
    
    // As we flesh out a UI for managing surveys, this will be elaborated.
    public List<Survey> getSurveys(Study study);
    
    public Survey getSurvey(String surveyGuid);
     
    public void deleteSurvey(String surveyGuid);

}
