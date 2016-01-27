package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Nonnull;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.play.interceptors.RequestUtils;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import play.Logger;
import play.cache.Cache;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Http.Request;
import play.mvc.Result;

import com.amazonaws.util.Throwables;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

public abstract class BaseController extends Controller {

    private static ObjectMapper mapper = BridgeObjectMapper.get();

    private BridgeConfig bridgeConfig;
    private CacheProvider cacheProvider;

    StudyService studyService;
    AuthenticationService authenticationService;

    @Autowired
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }
    BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    @Autowired
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Returns a session. Will not throw exception if user is not authorized or has not consented to research.
     * @return session if it exists, or null otherwise.
     */
    UserSession getSessionIfItExists() {
        final String sessionToken = getSessionToken();
        if (StringUtils.isBlank(sessionToken)){
            return null;
        }
        final UserSession session = authenticationService.getSession(sessionToken);
        writeSessionInfoToMetrics(session);
        return session;
    }

    /**
     * Retrieve a user's session or throw an exception if the user is not authenticated. 
     * User does not have to give consent. 
     */
    UserSession getAuthenticatedSession() throws NotAuthenticatedException {
        final UserSession session = getSessionIfItExists();
        if (session == null || !session.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        return session;
    }

    /**
     * Retrieve user's session using the Bridge-Session header or cookie, throwing an exception if the session doesn't
     * exist (user not authorized), consent has not been given or the client app version is not supported.
     */
    UserSession getAuthenticatedAndConsentedSession() throws NotAuthenticatedException, ConsentRequiredException, UnsupportedVersionException {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());        
        verifySupportedVersionOrThrowException(study);
        if (!session.getUser().doesConsent()) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }
    
    UserSession getAuthenticatedSession(Roles... roles) {
        checkNotNull(roles);
        
        UserSession session = getAuthenticatedSession();
        if (session.getUser().isInRole(roles)) {
            return session;
        }
        throw new UnauthorizedException();
    }
    
    void setSessionToken(String sessionToken) {
        response().setCookie(SESSION_TOKEN_HEADER, sessionToken, BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
    }

    void updateSessionUser(UserSession session, User user) {
        session.setUser(user);
        cacheProvider.setUserSession(session);
    }

    /** Package-scoped to make available in unit tests. */
    String getSessionToken() {
        String[] session = request().headers().get(SESSION_TOKEN_HEADER);
        if (session == null || session.length == 0 || session[0].isEmpty()) {
            Cookie sessionCookie = request().cookie(SESSION_TOKEN_HEADER);
            if (sessionCookie != null && sessionCookie.value() != null && !"".equals(sessionCookie.value())) {
                return sessionCookie.value();
            }
            return null;
        }
        return session[0];
    }
    
    void verifySupportedVersionOrThrowException(Study study) throws UnsupportedVersionException {
        ClientInfo clientInfo = getClientInfoFromUserAgentHeader();
        if (!clientInfo.isSupportedAppVersion(study.getMinSupportedAppVersions().get(clientInfo.getOsName()))) {
        	throw new UnsupportedVersionException(clientInfo);
        }
    }

    ClientInfo getClientInfoFromUserAgentHeader() {
        String userAgentHeader = request().getHeader(BridgeConstants.USER_AGENT_HEADER);
        ClientInfo info = ClientInfo.fromUserAgentCache(userAgentHeader);
        
        Logger.debug("User agent: '"+userAgentHeader+"' converted to " + info);
    	return info;
    }
    
    Result okResult(String message) {
        return ok(Json.toJson(new StatusMessage(message)));
    }

    Result okResult(Object obj) {
        return ok((JsonNode)mapper.valueToTree(obj));
    }
    
    <T> Result okResult(List<T> list) {
        return ok((JsonNode)mapper.valueToTree(new ResourceList<T>(list)));
    }
    
    Result createdResult(String message) throws Exception {
        return created(Json.toJson(new StatusMessage(message)));
    }
    
    Result createdResult(Object obj) throws Exception {
        return created((JsonNode)mapper.valueToTree(obj));
    }
    
    Result acceptedResult(String message) {
        return status(202, Json.toJson(new StatusMessage(message)));
    }
    
    // This is needed or tests fail. It appears to be a bug in Play Framework,
    // that the asJson() method doesn't return a node in that context, possibly
    // because the root object in the JSON is an array (which is legal). 
    JsonNode requestToJSON(Request request) {
        try {
            JsonNode node = request.body().asJson();
            if (node == null) {
                node = mapper.readTree(request().body().asText());
            }
            return node;
        } catch(Throwable e) {
            if (Throwables.getRootCause(e) instanceof InvalidEntityException) {
                throw (InvalidEntityException)Throwables.getRootCause(e);
            }
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
    static @Nonnull <T> T parseJson(Request request, Class<? extends T> clazz) {
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
            if (Throwables.getRootCause(ex) instanceof InvalidEntityException) {
                throw (InvalidEntityException)Throwables.getRootCause(ex);
            }
            throw new InvalidEntityException("Error parsing JSON in request body: " + ex.getMessage());    
        }
        throw new InvalidEntityException("Expected JSON in the request body is missing");
    }
    
    /**
     * Retrieves the metrics object from the cache. Can be null if the metrics is not in the cache.
     */
    Metrics getMetrics() {
        final String requestId = RequestUtils.getRequestId(request());
        final String cacheKey = Metrics.getCacheKey(requestId);
        return (Metrics)Cache.get(cacheKey);
    }

    /** Writes the user's stormpath token, internal session ID, and study ID to the metrics. */
    protected void writeSessionInfoToMetrics(UserSession session) {
        Metrics metrics = getMetrics();
        if (metrics != null && session != null) {
            metrics.setSessionId(session.getInternalSessionToken());
            User user = session.getUser();
            if (user != null) {
                metrics.setUserId(user.getId());
            }
            metrics.setStudy(session.getStudyIdentifier().getIdentifier());
        }
    }
}
