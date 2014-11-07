package controllers;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.services.SurveyResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public class SurveyResponseController extends BaseController {

    private static Logger logger = LoggerFactory.getLogger(SurveyResponseController.class);
    
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
        return createdResult(new GuidHolder(response.getGuid()));
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
    
    public Result deleteSurveyResponse(String guid) {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        
        responseService.deleteSurveyResponse(response);
        return okResult("Survey response deleted.");
    }

    private List<SurveyAnswer> deserializeSurveyAnswers() throws JsonProcessingException, IOException {
        JsonNode node = requestToJSON(request());
        List<SurveyAnswer> answers = JsonUtils.asEntityList(node, SurveyAnswer.class);
        return answers;
    }

    private SurveyResponse getSurveyResponseIfAuthorized(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        SurveyResponse response = responseService.getSurveyResponse(guid);
        if (!response.getHealthCode().equals(session.getUser().getHealthCode())) {
            logger.error("Blocked attempt to access survey response (mismatched health data codes) from user " + session.getUser().getId());
            throw new UnauthorizedException();
        }
        return response;
    }
    
}
