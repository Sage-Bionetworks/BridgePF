package controllers;

import models.JsonPayload;
import models.StatusMessage;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Result;

@org.springframework.stereotype.Controller
public class BaseController extends Controller {

    protected AuthenticationService authenticationService;
    
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    protected UserSession getSession() throws Exception {
        String sessionToken = getSessionToken();
        if (sessionToken == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        UserSession session = authenticationService.getSession(sessionToken);
        if (session == null || !session.isAuthenticated()) {
            throw new BridgeServiceException("Not signed in.", 401);
        } else if (!session.isConsent()) {
            throw new BridgeServiceException("Must consent to research study.", 412);
        }
        return session;
    }
    
    protected UserSession checkForSession() {
        String sessionToken = getSessionToken();
        return authenticationService.getSession(sessionToken);
    }
    
    protected void setSessionToken(String sessionToken) {
        response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken,
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
    }
    
    private String getSessionToken() {
        Cookie sessionCookie = request().cookie(BridgeConstants.SESSION_TOKEN_HEADER);
        if (sessionCookie != null && sessionCookie.value() != null && !"".equals(sessionCookie.value())) {
            return sessionCookie.value();
        }
        String[] session = request().headers().get(BridgeConstants.SESSION_TOKEN_HEADER);
        if (session == null || session.length == 0) {
            return null;
        }
        return session[0];
    }
    
	protected Result jsonResult(String message) {
		return ok(Json.toJson(new StatusMessage(message)));
	}

	protected Result jsonResult(JsonPayload<?> payload) {
		return ok(Json.toJson(payload));
	}

	protected Result jsonError(String message) {
		return internalServerError(Json.toJson(new StatusMessage(message)));
	}
}
