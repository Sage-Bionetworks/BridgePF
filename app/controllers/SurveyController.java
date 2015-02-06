package controllers;

import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;

import play.mvc.Result;

public class SurveyController extends BaseController {

    private SurveyService surveyService;
    
    private void verifySurveyIsInStudy(UserSession session, Study study, List<Survey> surveys) {
        if (!surveys.isEmpty()) {
            verifySurveyIsInStudy(session, study, surveys.get(0));
        }
    }
    private void verifySurveyIsInStudy(UserSession session, Study study, Survey survey) {
        if (!session.getUser().isInRole(BridgeConstants.ADMIN_GROUP) && 
            !survey.getStudyIdentifier().equals(study.getIdentifier())) {
            throw new UnauthorizedException();
        }
    }
    
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    public Result getAllSurveysMostRecentVersion() throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        verifySurveyIsInStudy(session, study, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentVersion2() throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        verifySurveyIsInStudy(session, study, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(study);
        verifySurveyIsInStudy(session, study, surveys);
        return okResult(surveys);
    }
    
    public Result getSurveyForUser(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(getStudyIdentifier());
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }

    public Result getSurveyMostRecentlyPublishedVersionForUser(String surveyGuid) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(getStudyIdentifier());
        
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(study, surveyGuid);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }
    
    // Otherwise you don't need consent but you must be a researcher or an administrator
    public Result getSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }
    
    public Result getSurveyMostRecentVersion(String surveyGuid) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = surveyService.getSurveyMostRecentVersion(study, surveyGuid);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }
    
    public Result getSurveyMostRecentlyPublishedVersion(String surveyGuid) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(study, surveyGuid);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }
    
    public Result getMostRecentPublishedSurveyVersionByIdentifier(String identifier) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersionByIdentifier(study, identifier);
        verifySurveyIsInStudy(session, study, survey);
        return okResult(survey);
    }
    
    public Result deleteSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        
        surveyService.deleteSurvey(study, survey);
        return okResult("Survey deleted.");
    }
    
    public Result getSurveyAllVersions(String surveyGuid) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        List<Survey> surveys = surveyService.getSurveyAllVersions(study, surveyGuid);
        verifySurveyIsInStudy(session, study, surveys);
        return okResult(surveys);
    }
    
    public Result createSurvey() throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        getAuthenticatedResearcherOrAdminSession(study);
        
        Survey survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setStudyIdentifier(study.getIdentifier());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        
        survey = surveyService.versionSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result updateSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        
        // The parameters in the URL take precedence over anything declared in 
        // the object itself.
        survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setGuid(surveyGuid);
        survey.setCreatedOn(createdOn);
        survey.setStudyIdentifier(study.getIdentifier());
        
        survey = surveyService.updateSurvey(survey);
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result publishSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
         
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        
        survey = surveyService.publishSurvey(survey);
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result closeSurvey(String surveyGuid, String createdOnString) throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        UserSession session = getAuthenticatedResearcherOrAdminSession(study);
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, study, survey);
        
        surveyService.closeSurvey(survey);
        return okResult("Survey closed.");
    }
}