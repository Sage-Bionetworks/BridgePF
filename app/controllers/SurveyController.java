package controllers;

import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.DateConverter;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;

import play.mvc.Result;

public class SurveyController extends BaseController {

    private SurveyService surveyService;
    
    private StudyControllerService studyControllerService;
    
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    
    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }
    
    public Result getAllSurveysAllVersions() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        List<Survey> surveys = surveyService.getSurveys(study);
        return ok(constructJSON(surveys));
    }
    
    public Result getMostRecentSurveys() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());

        List<Survey> surveys = surveyService.getMostRecentSurveys(study);
        return ok(constructJSON(surveys));
    }
    
    public Result getMostRecentlyPublishedSurveys() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());

        List<Survey> surveys = surveyService.getMostRecentlyPublishedSurveys(study);
        return ok(constructJSON(surveys));
    }
    
    public Result getSurvey(String surveyGuid, String versionString) throws Exception {
        // Just need to be signed in, anyone can get a survey to look at it.
        getAuthenticatedSession();
        
        long surveyVersion = DateConverter.convertMillisFromEpoch(versionString);
        Survey survey = surveyService.getSurvey(surveyGuid, surveyVersion);
        return ok(constructJSON(survey));
    }
    
    public Result getAllVersionsOfASurvey(String surveyGuid) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        List<Survey> surveys = surveyService.getAllVersionsOfSurvey(surveyGuid);
        return ok(constructJSON(surveys));
    }
    
    public Result createSurvey() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        Survey survey = DynamoSurvey.fromJson(requestToJSON(request()));
        survey.setStudyKey(study.getKey());
        
        survey = surveyService.createSurvey(survey);
        return ok(constructJSON(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        long surveyVersion = DateConverter.convertMillisFromEpoch(versionString);

        Survey survey = surveyService.versionSurvey(surveyGuid, surveyVersion);
        return ok(constructJSON(survey));
    }
    
    public Result updateSurvey(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        // The parameters in the URL take precedence over anything declared in 
        // the object itself.
        long surveyVersion = DateConverter.convertMillisFromEpoch(versionString);
        Survey survey = DynamoSurvey.fromJson(request().body().asJson());
        survey.setGuid(surveyGuid);
        survey.setVersionedOn(surveyVersion);
        survey.setStudyKey(study.getKey());
        
        survey = surveyService.updateSurvey(survey);
        return okResult("Survey updated.");
    }
    
    public Result publishSurvey(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        long surveyVersion = DateConverter.convertMillisFromEpoch(versionString);
        surveyService.publishSurvey(surveyGuid, surveyVersion);
        return okResult("Survey published.");
    }
    
    public Result closeSurvey(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyControllerService.getStudyByHostname(request());
        assertResearcherOrAdminUser(study, session.getUser());
        
        long surveyVersion = DateConverter.convertMillisFromEpoch(versionString);
        surveyService.closeSurvey(surveyGuid, surveyVersion);
        return okResult("The survey has been closed.");
    }
    
    private void assertResearcherOrAdminUser(Study study, User user) throws BridgeServiceException {
        Set<String> roles = user.getRoles();
        if (!roles.contains(BridgeConstants.ADMIN_GROUP) && !roles.contains(study.getResearcherRole())) {
            throw new UnauthorizedException();
        }
    }
}