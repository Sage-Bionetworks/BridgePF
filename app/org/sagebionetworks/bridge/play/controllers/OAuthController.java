package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

@Controller("oauthController")
public class OAuthController extends BaseController {
    
    private static final String AUTH_TOKEN = "authToken";
    private OAuthService service;
    
    @Autowired
    public final void setOAuthService(OAuthService service) {
        this.service = service;
    }

    public Result requestAccessToken(String vendorId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        JsonNode node = parseJson(request(), JsonNode.class);
        String token = node.has(AUTH_TOKEN) ? node.get(AUTH_TOKEN).textValue() : null;
        OAuthAuthorizationToken authToken = new OAuthAuthorizationToken(vendorId, token);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        OAuthAccessToken accessToken = service.requestAccessToken(study, session.getHealthCode(), authToken);
       
        return okResult(accessToken);
    }
    
    public Result getHealthCodesGrantingAccess(String studyIdentifier, String vendorId, String offsetKey, String pageSizeKey) {
        getAuthenticatedSession(Roles.WORKER);
        
        Study study = studyService.getStudy(studyIdentifier);
        int pageSize = BridgeUtils.getIntOrDefault(pageSizeKey, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<String> page = service.getHealthCodesGrantingAccess(study, vendorId, pageSize,
                offsetKey);
        
        return okResult(page);
    }
    
    public Result getAccessToken(String studyIdentifier, String vendorId, String healthCode) {
        getAuthenticatedSession(Roles.WORKER);
        
        Study study = studyService.getStudy(studyIdentifier);
        OAuthAccessToken accessToken = service.getAccessToken(study, vendorId, healthCode);
        
        return okResult(accessToken);
    }
    
}
