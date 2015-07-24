package org.sagebionetworks.bridge.play.controllers;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.sagebionetworks.bridge.services.SurveyResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class SurveyResponseController extends BaseController {
    
    private SurveyResponseService responseService;

    @Autowired
    public void setSurveyResponseService(SurveyResponseService responseService) {
        this.responseService = responseService;
    }

    // --- BETTER SURVEY RESPONSE API ---
    
    public Result createSurveyResponseV4() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        SurveyResponse res = parseJson(request(), DynamoSurveyResponse.class);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(
            res.getSurveyGuid(), res.getSurveyCreatedOn());
        
        String identifier = res.getIdentifier();
        if (identifier == null) {
            identifier = BridgeUtils.generateGuid();
        }
        
        SurveyResponseView response = responseService.createSurveyResponse(
            keys, session.getUser().getHealthCode(), res.getAnswers(), identifier);

        return createdResult(new IdentifierHolder(response.getIdentifier()));
    }
    
    public Result getSurveyResponseV4(String identifier) throws Exception {
        SurveyResponseView response = getSurveyResponseIfAuthorized(identifier);
        return okResult(response);
    }
    
    public Result appendAnswersToSurveyResponseV4(String identifier) throws Exception {
        SurveyResponseView response = getSurveyResponseIfAuthorized(identifier);
        
        SurveyResponse res = parseJson(request(), DynamoSurveyResponse.class);
        responseService.appendSurveyAnswers(response.getResponse(), res.getAnswers());
        return okResult("Survey response updated.");
    }

    // --- END BETTER SURVEY RESPONSE API ---
    
    public Result createSurveyResponse(String surveyGuid, String versionString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        Long version = DateUtils.convertToMillisFromEpoch(versionString);
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, version);
        SurveyResponseView response = responseService
                .createSurveyResponse(keys, session.getUser().getHealthCode(), answers);
        return createdResult(new IdentifierHolder(response.getIdentifier()));
    }
    
    public Result createSurveyResponseWithIdentifier(String surveyGuid, String versionString, String identifier)
            throws Exception {
        
        UserSession session = getAuthenticatedAndConsentedSession();
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        Long version = DateUtils.convertToMillisFromEpoch(versionString);

        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, version);
        SurveyResponseView response = responseService.createSurveyResponse(keys, 
                session.getUser().getHealthCode(), answers, identifier);
        return createdResult(new IdentifierHolder(response.getIdentifier()));
    }

    public Result getSurveyResponse(String identifier) throws Exception {
        SurveyResponseView response = getSurveyResponseIfAuthorized(identifier);
        return okResult(response);
    }
    
    public Result appendSurveyAnswers(String identifier) throws Exception {
        SurveyResponseView response = getSurveyResponseIfAuthorized(identifier);
        
        List<SurveyAnswer> answers = deserializeSurveyAnswers();
        responseService.appendSurveyAnswers(response.getResponse(), answers);
        return okResult("Survey response updated.");
    }

    private List<SurveyAnswer> deserializeSurveyAnswers() throws JsonProcessingException, IOException {
        JsonNode node = requestToJSON(request());
        return JsonUtils.asEntityList(node, SurveyAnswer.class);
    }

    private SurveyResponseView getSurveyResponseIfAuthorized(String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String healthCode = session.getUser().getHealthCode(); 
        return responseService.getSurveyResponse(healthCode, identifier);
    }
    
}
