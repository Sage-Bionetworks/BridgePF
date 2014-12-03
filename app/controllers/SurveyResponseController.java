package controllers;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.IdentifierHolder;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.services.SurveyResponseService;

import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public class SurveyResponseController extends BaseController {
    
    private SurveyResponseService responseService;
    
    public void setSurveyResponseService(SurveyResponseService responseService) {
        this.responseService = responseService;
    }
    
    public Result createSurveyResponse(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        Long version = DateUtils.convertToMillisFromEpoch(versionString);
        
        SurveyResponse response = responseService.createSurveyResponse(
            surveyGuid, version, session.getUser().getHealthCode(), answers);
        return createdResult(new IdentifierHolder(response.getIdentifier()));
    }
    
    public Result createSurveyResponseWithIdentifier(String surveyGuid, String versionString, String identifier)
            throws Exception {
        
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        Long version = DateUtils.convertToMillisFromEpoch(versionString);

        SurveyResponse response = responseService.createSurveyResponse(
            surveyGuid, version, session.getUser().getHealthCode(), answers, identifier);
        return createdResult(new IdentifierHolder(response.getIdentifier()));
    }

    public Result getSurveyResponse(String guid) throws Exception {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        return okResult(response);
    }
    
    public Result appendSurveyAnswers(String guid) throws Exception {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        responseService.appendSurveyAnswers(response, answers);
        return okResult("Survey response updated.");
    }
    
    public Result deleteSurveyResponse(String identifier) {
        SurveyResponse response = getSurveyResponseIfAuthorized(identifier);
        
        responseService.deleteSurveyResponse(response);
        return okResult("Survey response deleted.");
    }

    private List<SurveyAnswer> deserializeSurveyAnswers() throws JsonProcessingException, IOException {
        JsonNode node = requestToJSON(request());
        List<SurveyAnswer> answers = JsonUtils.asEntityList(node, SurveyAnswer.class);
        return answers;
    }

    private SurveyResponse getSurveyResponseIfAuthorized(String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String healthCode = session.getUser().getHealthCode(); 
        return responseService.getSurveyResponse(healthCode, identifier);
    }
    
}
