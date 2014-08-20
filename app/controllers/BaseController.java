package controllers;

import java.io.IOException;

import models.StatusMessage;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Result;

@org.springframework.stereotype.Controller
public abstract class BaseController extends Controller {

    protected AuthenticationService authenticationService;
    protected CacheProvider cacheProvider;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    /**
     * Retrieve user's session using the Bridge-Session header or cookie, throwing 
     * an exception if the session doesn't exist (user not authorized) or consent 
     * has not been given.
     * @return
     * @throws Exception
     */
    protected UserSession getSession() throws Exception {
        String sessionToken = getSessionToken();
        if (sessionToken == null || sessionToken.isEmpty()) {
            throw new BridgeServiceException("Not signed in.", 401);
        }

        UserSession session = authenticationService.getSession(sessionToken);
        if (session == null || !session.isAuthenticated()) {
            throw new BridgeServiceException("Not signed in.", 401);
        } else if (!session.getUser().isConsent()) {
            throw new BridgeServiceException("Must consent to research study.", 412);
        }
        return session;
    }

    /**
     * Return a session if it exists, or null otherwise. Will not throw exception if 
     * user is not authorized or has not consented to research. 
     * @return
     */
    protected UserSession checkForSession() {
        String sessionToken = getSessionToken();
        return authenticationService.getSession(sessionToken);
    }

    protected void setSessionToken(String sessionToken) {
        response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken,
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
    }
    
    protected void updateSessionUser(UserSession session, User user) {
        session.setUser(user);
        cacheProvider.setUserSession(session.getSessionToken(), session);
    }

    private String getSessionToken() {
        Cookie sessionCookie = request().cookie(BridgeConstants.SESSION_TOKEN_HEADER);
        if (sessionCookie != null && sessionCookie.value() != null && !"".equals(sessionCookie.value())) {
            return sessionCookie.value();
        }
        String[] session = request().headers().get(BridgeConstants.SESSION_TOKEN_HEADER);
        if (session == null || session.length == 0 || session[0].isEmpty()) {
            return null;
        }
        return session[0];
    }

    protected Result okResult(String message) {
        return ok(Json.toJson(new StatusMessage(message)));
    }

    protected Result errorResult(String message) {
        return internalServerError(Json.toJson(new StatusMessage(message)));
    }

    // This is needed or tests fail. It appears to be a bug in Play Framework,
    // that the asJson() method doesn't return a node in that context, possibly
    // because the root object in the JSON is an array (which is legal). OTOH,
    // if asJson() works, you will get an error if you call asText(), as Play
    // seems to only allow processing the body content one time in a request.
    protected JsonNode requestToJSON(Request request) throws JsonProcessingException, IOException {
        JsonNode node = request().body().asJson();
        if (node == null) {
            ObjectMapper mapper = new ObjectMapper();
            node = mapper.readTree(request().body().asText());
        }
        return node;
    }
}
