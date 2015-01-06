package controllers;

import java.util.Collection;

import com.google.common.base.Strings;
import models.StatusMessage;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class BaseController extends Controller {

    private static ObjectMapper mapper = BridgeObjectMapper.get();
    
    protected AuthenticationService authenticationService;
    protected StudyService studyService;
    protected CacheProvider cacheProvider;
    protected BridgeConfig bridgeConfig;
    
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }
    
    /**
     * Retrieve a user's session or throw an exception if the user is not authenticated. 
     * User does not have to give consent. 
     * @return
     * @throws Exception
     */
    protected UserSession getAuthenticatedSession() throws BridgeServiceException {
        String sessionToken = getSessionToken();
        if (sessionToken == null || sessionToken.isEmpty()) {
            throw new NotAuthenticatedException();
        }

        UserSession session = authenticationService.getSession(sessionToken);
        if (session == null || !session.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        return session;
    }
    
    /**
     * Retrieve user's session using the Bridge-Session header or cookie, throwing an exception if the session doesn't
     * exist (user not authorized) or consent has not been given.
     * 
     * @return
     * @throws Exception
     */
    protected UserSession getAuthenticatedAndConsentedSession() throws BridgeServiceException {
        String sessionToken = getSessionToken();
        if (sessionToken == null || sessionToken.isEmpty()) {
            throw new NotAuthenticatedException();
        }

        UserSession session = authenticationService.getSession(sessionToken);
        if (session == null || !session.isAuthenticated()) {
            throw new NotAuthenticatedException();
        } else if (!session.getUser().isConsent()) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    /**
     * Checks if the user is in the "admin" group.
     */
    protected UserSession getAuthenticatedAdminSession() throws BridgeServiceException {
        UserSession session = getAuthenticatedSession();
        if (!session.getUser().isInRole(BridgeConstants.ADMIN_GROUP)) {
            throw new UnauthorizedException();
        }
        return session;
    }
    
    protected UserSession getAuthenticatedResearcherOrAdminSession(Study study) {
        UserSession session = getAuthenticatedSession();
        User user = session.getUser();
        if (user.isInRole(BridgeConstants.ADMIN_GROUP) || user.isInRole(study.getResearcherRole())) {
            return session;
        }
        throw new UnauthorizedException();
    }
    
    protected UserSession getAuthenticatedResearchOrAdminSession(Study study) {
        UserSession session = getAuthenticatedSession();
        User user = session.getUser();
        if (user.isInRole(BridgeConstants.ADMIN_GROUP) || user.isInRole(study.getResearcherRole())) {
            return session;
        }
        throw new UnauthorizedException();
    }
    /**
     * Return a session if it exists, or null otherwise. Will not throw exception if user is not authorized or has not
     * consented to research.
     * 
     * @return
     */
    protected UserSession getSessionIfItExists() {
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

    protected String getHostname() {
        // For testing, we check a few places for a forced host value, first 
        // from the configuration, and then on every request, as a header from
        // the client.
        if (!bridgeConfig.isProduction()) {
            if (bridgeConfig.getHost() != null) {
                return bridgeConfig.getHost();
            }
            String header = request().getHeader("Bridge-Host");
            if (header != null) {
                return header;
            }
        }
        String host = request().host();
        if (host.indexOf(":") > -1) {
            host = host.split(":")[0];
        }
        return host;
    }
    
    private String getSessionToken() {
        String[] session = request().headers().get(BridgeConstants.SESSION_TOKEN_HEADER);
        if (session == null || session.length == 0 || session[0].isEmpty()) {
            Cookie sessionCookie = request().cookie(BridgeConstants.SESSION_TOKEN_HEADER);
            if (sessionCookie != null && sessionCookie.value() != null && !"".equals(sessionCookie.value())) {
                return sessionCookie.value();
            }
            return null;
        }
        return session[0];
    }

    protected Result okResult(String message) {
        return ok(Json.toJson(new StatusMessage(message)));
    }
    
    protected Result okResult(Object obj) throws Exception {
        return ok(mapper.valueToTree(obj));
    }
    
    protected Result okResult(Collection<?> items) throws Exception {
        ArrayNode itemsNode = mapper.createArrayNode();
        for (Object item : items) {
            ObjectNode node = (ObjectNode) mapper.valueToTree(item);
            itemsNode.add(node);
        }
        ObjectNode json = mapper.createObjectNode();
        json.set("items", itemsNode);
        json.put("total", items.size());
        json.put("type", "ResourceList");
        return ok(json);        
    }

    protected Result errorResult(String message) {
        return internalServerError(Json.toJson(new StatusMessage(message)));
    }
    
    protected Result createdResult(Object obj) throws Exception {
        return created(mapper.valueToTree(obj));
    }

    // This is needed or tests fail. It appears to be a bug in Play Framework,
    // that the asJson() method doesn't return a node in that context, possibly
    // because the root object in the JSON is an array (which is legal). OTOH,
    // if asJson() works, you will get an error if you call asText(), as Play
    // seems to only allow processing the body content one time in a request.
    protected JsonNode requestToJSON(Request request) {
        try {
            JsonNode node = request().body().asJson();
            if (node == null) {
                node = mapper.readTree(request().body().asText());
            }
            return node;
        } catch(Throwable e) {
            throw new InvalidEntityException("Expected JSON in the request body is missing or malformed");
        }
    }

    protected static <T> T parseJson(Request request, Class<? extends T> clazz) {
        try {
            // Whether asText() or asJson() works depends on the content-type header of the request
            String jsonText = request.body().asText();
            if (!Strings.isNullOrEmpty(jsonText)) {
                return mapper.readValue(jsonText, clazz);
            }

            JsonNode jsonNode = request.body().asJson();
            if (jsonNode != null) {
                return mapper.convertValue(jsonNode, clazz);
            }
        } catch (Throwable ex) {
            throw new InvalidEntityException("Error parsing JSON in request body");
        }

        throw new InvalidEntityException("Expected JSON in the request body is missing");
    }
}
