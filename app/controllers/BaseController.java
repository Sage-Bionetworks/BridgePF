package controllers;

import javax.annotation.Nonnull;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static Logger logger = LoggerFactory.getLogger(BaseController.class);
    
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
    
    protected UserSession getAuthenticatedResearcherSession(Study study) {
        UserSession session = getAuthenticatedSession();
        User user = session.getUser();
        if (user.isInRole(study.getResearcherRole())) {
            return session;
        }
        throw new UnauthorizedException();
    }
    
    protected UserSession getAuthenticatedResearcherOrAdminSession(Study study) {
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
    
    protected Study getStudy() {
        String studyIdentifier = getStudyIdentifier();
        return studyService.getStudy(studyIdentifier);
    }
    
    protected String getStudyIdentifier() {
        // Bridge-Study: api
        String value = request().getHeader(BridgeConstants.BRIDGE_STUDY_HEADER);
        if (value != null) {
            logger.debug("Study identifier retrieved from Bridge-Study header ("+value+")");
            return value;
        }
        // Bridge-Host: api-develop.sagebridge.org
        value = request().getHeader(BridgeConstants.BRIDGE_HOST_HEADER);
        if (value != null) {
            logger.debug("Study identifier parsed from Bridge-Host header ("+value+")");
            return getIdentifierFromHostname(value);
        }
        // bridge.conf:
        // host = api-local.sagebridge.org
        value = bridgeConfig.getHost();
        if (bridgeConfig.isLocal() && value != null) {
            logger.debug("Study identifier parsed from host property in config file ("+value+")");
            return getIdentifierFromHostname(value);
        }
        // Host: api-develop.sagebridge.org
        value = request().host();
        logger.warn("Study identifier retrieved from Host header ("+value+")");
        return getIdentifierFromHostname(value);
    }
    
    private String getIdentifierFromHostname(String hostname) {
        if (hostname.indexOf(":") > -1) {
            hostname = hostname.split(":")[0];
        }
        String postfix = bridgeConfig.getStudyHostnamePostfix();
        return (postfix == null) ? "api" : hostname.split(postfix)[0];
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
    
    protected Result okResult(Object obj) {
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

    /**
     * Static utility function that parses the JSON from the given request as the given class. This is a wrapper around
     * Jackson.
     *
     * @param request
     *         Play framework request
     * @param clazz
     *         class to parse the JSON as
     * @return object parsed from JSON, will be non-null
     */
    protected static @Nonnull <T> T parseJson(Request request, Class<? extends T> clazz) {
        try {
            // Calling request.body() twice is safe. (Has been confirmed using "play debug" and stepping through this
            // code in a debugger.)
            // Whether asText() or asJson() works depends on the content-type header of the request
            // asText() returns data if the content-type is text/plain. asJson() returns data if the content-type is
            // text/json or application/json.
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
