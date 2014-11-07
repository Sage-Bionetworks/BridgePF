package controllers;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;

import play.mvc.Result;

public class SurveyController extends ResearcherController {

    private SurveyService surveyService;
    
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    public Result getAllSurveysAllVersions() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        List<Survey> surveys = surveyService.getSurveys(study);
        return okResult(surveys);
    }
    
    public Result getMostRecentSurveys() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getMostRecentSurveys(study);
        return okResult(surveys);
    }
    
    public Result getMostRecentlyPublishedSurveys() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getMostRecentlyPublishedSurveys(study);
        return okResult(surveys);
    }
    
    public Result getSurveyForUser(String surveyGuid, String createdOnString) throws Exception {
        getAuthenticatedAndConsentedSession();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        Survey survey = surveyService.getSurvey(surveyGuid, createdOn);
        if (!survey.isPublished()) {
            throw new EntityNotFoundException(Survey.class);
        }
        return okResult(survey);
    }
    
    // Otherwise you don't need consent but you must be a researcher or an administrator
    public Result getSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        Survey survey = surveyService.getSurvey(surveyGuid, createdOn);
        return okResult(survey);
    }
    
    public Result deleteSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        surveyService.deleteSurvey(surveyGuid, createdOn);
        return okResult("Survey deleted.");
    }
    
    public Result getAllVersionsOfASurvey(String surveyGuid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        List<Survey> surveys = surveyService.getAllVersionsOfSurvey(surveyGuid);
        return okResult(surveys);
    }
    
    public Result createSurvey() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setStudyKey(study.getKey());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolder(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        Survey survey = surveyService.versionSurvey(surveyGuid, createdOn);
        return createdResult(new GuidCreatedOnVersionHolder(survey));
    }
    
    public Result updateSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        // The parameters in the URL take precedence over anything declared in 
        // the object itself.
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        Survey survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setGuid(surveyGuid);
        survey.setCreatedOn(createdOn);
        survey.setStudyKey(study.getKey());
        
        survey = surveyService.updateSurvey(survey);
        return okResult(new GuidCreatedOnVersionHolder(survey));
    }
    
    public Result publishSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        surveyService.publishSurvey(surveyGuid, createdOn);
        return okResult("Survey published.");
    }
    
    public Result closeSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        surveyService.closeSurvey(surveyGuid, createdOn);
        return okResult("Survey closed.");
    }
}