package controllers;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public class SurveyResponseController extends BaseController {

    private static Logger logger = LoggerFactory.getLogger(SurveyResponseController.class);
    
    private SurveyResponseDao responseDao;
    
    public void setSurveyResponseDao(SurveyResponseDao responseDao) {
        this.responseDao = responseDao;
    }
    
    public Result createSurveyResponse(String surveyGuid, Long surveyVersion) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        
        SurveyResponse response = responseDao.createSurveyResponse(
            surveyGuid, surveyVersion, session.getUser().getHealthDataCode(), answers);
        return ok(constructJSON(new GuidHolder(response.getGuid())));
    }
    
    public Result getSurveyResponse(String guid) throws Exception {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        return ok(constructJSON(response));
    }
    
    public Result appendSurveyAnswers(String guid) throws Exception {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        responseDao.appendSurveyAnswers(response, answers);
        return okResult("Survey response updated.");
    }
    
    public Result deleteSurveyResponse(String guid) {
        SurveyResponse response = getSurveyResponseIfAuthorized(guid);
        
        responseDao.deleteSurveyResponse(response);
        return okResult("Survey response deleted.");
    }

    private List<SurveyAnswer> deserializeSurveyAnswers() throws JsonProcessingException, IOException {
        JsonNode node = requestToJSON(request());
        List<SurveyAnswer> answers = JsonUtils.asEntityList(node, SurveyAnswer.class);
        return answers;
    }

    private SurveyResponse getSurveyResponseIfAuthorized(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        SurveyResponse response = responseDao.getSurveyResponse(guid);
        if (!response.getHealthCode().equals(session.getUser().getHealthDataCode())) {
            logger.error("Blocked attempt to access survey response (mismatched health data codes) from user " + session.getUser().getId());
            throw new UnauthorizedException();
        }
        return response;
    }
    
}
