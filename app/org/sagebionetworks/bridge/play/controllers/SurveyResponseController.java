package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.sagebionetworks.bridge.services.SurveyResponseService;
import org.sagebionetworks.bridge.validators.SurveyResponseValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class SurveyResponseController extends BaseController {
    
    private SurveyResponseService responseService;

    @Autowired
    public void setSurveyResponseService(SurveyResponseService responseService) {
        this.responseService = responseService;
    }
    
    public Result createSurveyResponse() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        final SurveyResponse res = parseJson(request(), DynamoSurveyResponse.class);
        Validate.entityThrowingException(new SurveyResponseValidator(), res);
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(res.getSurveyGuid(), res.getSurveyCreatedOn());
        
        SurveyResponseView view = responseService.createSurveyResponse(keys, 
            session.getUser().getHealthCode(), res.getAnswers(), res.getIdentifier());

        return createdResult(new IdentifierHolder(view.getIdentifier()));
    }
    
    public Result getSurveyResponse(String identifier) throws Exception {
        SurveyResponseView view = getSurveyResponseIfAuthorized(identifier);
        return okResult(view);
    }
    
    public Result appendSurveyAnswers(String identifier) throws Exception {
        SurveyResponseView view = getSurveyResponseIfAuthorized(identifier);

        // Get the answers. We have the survey keys, etc. given the identifier.
        SurveyResponse res = parseJson(request(), DynamoSurveyResponse.class);
        
        responseService.appendSurveyAnswers(view.getResponse(), res.getAnswers());
        return okResult("Survey response updated.");
    }

    SurveyResponseView getSurveyResponseIfAuthorized(String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String healthCode = session.getUser().getHealthCode(); 
        return responseService.getSurveyResponse(healthCode, identifier);
    }
    
}
