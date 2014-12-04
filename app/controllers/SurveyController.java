package controllers;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;

import play.mvc.Result;

public class SurveyController extends BaseController {

    private SurveyService surveyService;
    
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    public Result getAllSurveysMostRecentVersion() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentVersion2() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(study);
        return okResult(surveys);
    }
    
    public Result getSurveyForUser(String surveyGuid, String createdOnString) throws Exception {
        getAuthenticatedAndConsentedSession();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        Survey survey = surveyService.getSurvey(keys);
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
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        Survey survey = surveyService.getSurvey(keys);
        return okResult(survey);
    }
    
    public Result getSurveyMostRecentVersion(String surveyGuid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = surveyService.getSurveyMostRecentVersion(study, surveyGuid);
        return okResult(survey);
    }
    
    public Result getSurveyMostRecentlyPublishedVersion(String surveyGuid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(study, surveyGuid);
        return okResult(survey);
    }
    
    public Result deleteSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        surveyService.deleteSurvey(study, keys);
        return okResult("Survey deleted.");
    }
    
    public Result getSurveyAllVersions(String surveyGuid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        List<Survey> surveys = surveyService.getSurveyAllVersions(study, surveyGuid);
        return okResult(surveys);
    }
    
    public Result createSurvey() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setStudyIdentifier(study.getIdentifier());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        Survey survey = surveyService.versionSurvey(keys);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
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
        survey.setStudyIdentifier(study.getIdentifier());
        
        survey = surveyService.updateSurvey(survey);
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result publishSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
         
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        surveyService.publishSurvey(keys);
        return okResult("Survey published.");
    }
    
    public Result closeSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        surveyService.closeSurvey(keys);
        return okResult("Survey closed.");
    }
}