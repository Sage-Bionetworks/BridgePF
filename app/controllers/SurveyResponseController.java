package controllers;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

public class SurveyResponseController extends BaseController {

    private SurveyResponseDao responseDao;
    
    public void setSurveyResponseDao(SurveyResponseDao responseDao) {
        this.responseDao = responseDao;
    }
    
    public Result createSurveyResponse(String surveyGuid, Long surveyVersion) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        
        SurveyResponse response = responseDao.createSurveyResponse(
            surveyGuid, surveyVersion, session.getUser().getHealthDataCode(), answers);
        return ok(constructJSON(response));
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
        List<SurveyAnswer> answers = JsonUtils.asSurveyAnswers(node);
        return answers;
    }

    private SurveyResponse getSurveyResponseIfAuthorized(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        SurveyResponse response = responseDao.getSurveyResponse(guid);
        if (!response.getHealthCode().equals(session.getUser().getHealthDataCode())) {
            throw new UnauthorizedException();
        }
        return response;
    }
    
}
