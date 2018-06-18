package org.sagebionetworks.bridge.play.controllers;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.play.interceptors.RequestUtils;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import play.cache.Cache;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;

import com.amazonaws.util.Throwables;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

public abstract class BaseController extends Controller {

    private final static Logger LOG = LoggerFactory.getLogger(BaseController.class);
    
    protected final static ObjectMapper MAPPER = BridgeObjectMapper.get();

    CacheProvider cacheProvider;
    
    BridgeConfig bridgeConfig;
    
    AccountDao accountDao;

    StudyService studyService;

    AuthenticationService authenticationService;
    
    SessionUpdateService sessionUpdateService;

    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Autowired
    final void setSessionUpdateService(SessionUpdateService sessionUpdateService) {
        this.sessionUpdateService = sessionUpdateService;
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
     * Retrieve user's session using the Bridge-Session header, throwing an exception if the session doesn't
     * exist (user not authorized), consent has not been given or the client app version is not supported.
     */
    UserSession getAuthenticatedAndConsentedSession() throws NotAuthenticatedException, ConsentRequiredException, UnsupportedVersionException {
        return getAuthenticatedSession(true);
    }

    /**
     * Retrieve a user's session or throw an exception if the user is not authenticated. User does not have to give
     * consent. If roles are provided, user must have one of the specified roles or an authorization exception will be
     * thrown. If no roles are supplied, the user just needs to be authenticated.
     */
    UserSession getAuthenticatedSession(Roles... roles) throws NotAuthenticatedException, UnauthorizedException {
        return getAuthenticatedSession(false, roles);
    }
    
    /**
     * Return a session if the user is a consented participant, OR if the user has one of the supplied roles. If no
     * roles are supplied, this method returns the session only if the caller is a consented participant.
     */
    UserSession getSessionEitherConsentedOrInRole(Roles... roles) throws NotAuthenticatedException,
            ConsentRequiredException, UnsupportedVersionException, UnauthorizedException {
        
        return getAuthenticatedSession(true, roles);
    }
    
    /**
     * This method centralizes session checking. If consent is required, user must be consented, if roles are supplied,
     * the user must have one of the roles, and if both are provided, the user must be EITHER consented OR in one of the
     * given roles. If neither is supplied (<code>getAuthenticatedSession(false)</code>), than you just need to be
     * authenticated. This method also ensures that the user's app version is up-to-date if consent is required.
     */
    UserSession getAuthenticatedSession(boolean consentRequired, Roles...roles) {
        final UserSession session = getSessionIfItExists();
        if (session == null || !session.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }

        // Sessions are locked to an IP address if (a) it is enabled in the study for unprivileged participant accounts
        // or (b) always for privileged accounts.
        Study study = studyService.getStudy(session.getStudyIdentifier());
        Set<Roles> userRoles = session.getParticipant().getRoles();
        boolean userHasRoles = !userRoles.isEmpty();
        if (study.isParticipantIpLockingEnabled() || userHasRoles) {
            String sessionIpAddress = parseIpAddress(session.getIpAddress());
            String requestIpAddress = parseIpAddress(getRemoteAddress());
            if (!Objects.equals(sessionIpAddress, requestIpAddress)) {
                throw new NotAuthenticatedException();
            }
        }

        // Any method that can throw a 412 can also throw a 410 (min app version not met).
        if (consentRequired) {
            verifySupportedVersionOrThrowException(study);
        }
        
        // if there are roles, they are required
        boolean rolesRequired = (roles != null && roles.length > 0); 
        boolean isInRole = (rolesRequired) ? !Collections.disjoint(Sets.newHashSet(roles), userRoles) : false;
        
        if ((consentRequired && session.doesConsent()) || (rolesRequired && isInRole)) {
            return session;
        }

        // Behavior here is unusual. It privileges the UnauthorizedException first for users with roles, 
        // and the ConsentRequiredException first for users without any roles.
        if (userHasRoles && rolesRequired && !isInRole) {
            throw new UnauthorizedException();
        }
        if (consentRequired && !session.doesConsent()) {
            throw new ConsentRequiredException(session);
        }
        if (rolesRequired && !isInRole) {
            throw new UnauthorizedException();
        }
        // If you get here, then all that was requested was an authenticated user, 
        // user doesn't need to be consented or to possess any specific role.
        return session;
    }

    /** Package-scoped to make available in unit tests. */
    String getSessionToken() {
        String[] session = request().headers().get(SESSION_TOKEN_HEADER);
        if (session != null && session.length > 0 && !session[0].isEmpty()) {
            return session[0];
        }
        return null;
    }
    
    void verifySupportedVersionOrThrowException(Study study) throws UnsupportedVersionException {
        ClientInfo clientInfo = getClientInfoFromUserAgentHeader();
        String osName = clientInfo.getOsName();
        Integer minVersionForOs = study.getMinSupportedAppVersions().get(osName);
        
        if (!clientInfo.isSupportedAppVersion(minVersionForOs)) {
            throw new UnsupportedVersionException(clientInfo);
        }
    }

    /**
     * Once we acquire a language for a user, we save it and use that language going forward. Changing their 
     * language in the host operating system will not change the language they are using (since changing the 
     * language might change their consent state). If they change their language by updating their UserProfile, 
     * then they may have to reconsent in the new language they are using for the study. Any warnings to 
     * that effect will need to be included in the application.
     */
    LinkedHashSet<String> getLanguages(UserSession session) {
        StudyParticipant participant = session.getParticipant();
        if (!participant.getLanguages().isEmpty()) {
            return participant.getLanguages();
        }
        LinkedHashSet<String> languages = getLanguagesFromAcceptLanguageHeader();
        if (!languages.isEmpty()) {
            accountDao.editAccount(session.getStudyIdentifier(), session.getHealthCode(),
                    account -> account.setLanguages(languages));
            
            sessionUpdateService.updateLanguage(session, languages);
        }
        return languages;
    }
    
    /**
     * Returns languages in the order of their quality rating in the original LanguageRange objects 
     * that are created from the Accept-Language header (first item in ordered set is the most-preferred 
     * language option).
     * @return
     */
    LinkedHashSet<String> getLanguagesFromAcceptLanguageHeader() {
        String acceptLanguageHeader = request().getHeader(ACCEPT_LANGUAGE);
        if (isNotBlank(acceptLanguageHeader)) {
            try {
                List<LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguageHeader);
                return ranges.stream().map(range -> {
                    return Locale.forLanguageTag(range.getRange()).getLanguage();
                }).collect(Collectors.toCollection(LinkedHashSet::new));
            } catch(IllegalArgumentException e) {
                // Accept-Language header was not properly formatted, do not throw an exception over 
                // a malformed header, just return that no languages were found.
                LOG.debug("Malformed Accept-Language header sent: " + acceptLanguageHeader);
            }
        }

        // if no Accept-Language header detected, we shall add an extra warning header
        addWarningMessage(BridgeConstants.WARN_NO_ACCEPT_LANGUAGE);
        return new LinkedHashSet<>();
    }
    
    ClientInfo getClientInfoFromUserAgentHeader() {
        String userAgentHeader = request().getHeader(USER_AGENT);
        ClientInfo info = ClientInfo.fromUserAgentCache(userAgentHeader);

        // if the user agent cannot be parsed (probably due to missing user agent string or unrecognizable user agent),
        // should set an extra header to http response as warning - we should have an user agent info for filtering to work
        if (info.equals(ClientInfo.UNKNOWN_CLIENT)) {
            addWarningMessage(BridgeConstants.WARN_NO_USER_AGENT);
        }
        LOG.debug("User-Agent: '"+userAgentHeader+"' converted to " + info);    
    	    return info;
    }

    CriteriaContext getCriteriaContext(StudyIdentifier studyId) {
        return new CriteriaContext.Builder()
            .withStudyIdentifier(studyId)
            .withLanguages(getLanguagesFromAcceptLanguageHeader())
            .withClientInfo(getClientInfoFromUserAgentHeader())
            .withIpAddress(getRemoteAddress())
            .build();
    }
    
    CriteriaContext getCriteriaContext(UserSession session) {
        checkNotNull(session);
        
        return new CriteriaContext.Builder()
            .withLanguages(session.getParticipant().getLanguages())
            .withClientInfo(getClientInfoFromUserAgentHeader())
            .withHealthCode(session.getHealthCode())
            .withIpAddress(session.getIpAddress())
            .withUserId(session.getId())
            .withUserDataGroups(session.getParticipant().getDataGroups())
            .withStudyIdentifier(session.getStudyIdentifier())
            .build();
    }
    
    Result okResult(String message) {
        return ok(Json.toJson(new StatusMessage(message)));
    }

    Result okResult(Object obj) {
        return ok((JsonNode)MAPPER.valueToTree(obj));
    }
    
    <T> Result okResult(List<T> list) {
        return ok((JsonNode)MAPPER.valueToTree(new ResourceList<T>(list)));
    }
    
    Result okResult(ObjectWriter writer, Object object) throws JsonGenerationException, JsonMappingException, IOException {
        return ok( writer.writeValueAsString(object) ).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    Result createdResult(String message)  {
        return created(Json.toJson(new StatusMessage(message)));
    }
    
    Result createdResult(Object obj) {
        return created((JsonNode)MAPPER.valueToTree(obj));
    }
    
    Result createdResult(ObjectWriter writer, Object object) throws JsonGenerationException, JsonMappingException, IOException {
        return created( writer.writeValueAsString(object) ).as(BridgeConstants.JSON_MIME_TYPE);
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
                node = MAPPER.readTree(request().body().asText());
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
                return MAPPER.readValue(jsonText, clazz);
            }

            JsonNode jsonNode = request.body().asJson();
            if (jsonNode != null) {
                return MAPPER.convertValue(jsonNode, clazz);
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
        final String cacheKey = Metrics.getCacheKey(getRequestId());
        return (Metrics)Cache.get(cacheKey);
    }

    /** Helper method which abstracts away getting the request ID from the request. */
    protected String getRequestId() {
        return RequestUtils.getRequestId(request());
    }

    /** The user's IP Address, as reported by Amazon. Package-scoped for unit tests. */
    String getRemoteAddress() {
        return RequestUtils.header(request(), X_FORWARDED_FOR_HEADER, request().remoteAddress());
    }

    // Helper method to parse an IP address from a raw string, as specified by getRemoteAddress().
    private static String parseIpAddress(String fullIpAddressString) {
        if (isBlank(fullIpAddressString)) {
            // Canonicalize unspecified IP address to null.
            return null;
        }

        // Remote address comes from the X-Forwarded-For header. Since we're behind Amazon, this is almost always
        // 2 IP addresses, separated by a comma and a space. The second is an Amazon router. The first one is probably
        // the real IP address.
        //
        // Note that this isn't fool-proof. X-Forwarded-For can be spoofed. Also, double-proxies might exist, or the
        // first IP address might simply resolve to 192.168.X.1. In local, this is probably just 127.0.0.1. But at
        // least this is an added layer of defense vs not IP-locking at all.
        String[] ipAddressArray = fullIpAddressString.split(",");
        return ipAddressArray[0];
    }

    /** Writes the user's account ID, internal session ID, and study ID to the metrics. */
    protected void writeSessionInfoToMetrics(UserSession session) {
        Metrics metrics = getMetrics();
        if (metrics != null && session != null) {
            metrics.setSessionId(session.getInternalSessionToken());
            metrics.setUserId(session.getId());
            metrics.setStudy(session.getStudyIdentifier().getIdentifier());
        }
    }
    
    protected RequestInfo.Builder getRequestInfoBuilder(UserSession session) {
        checkNotNull(session);
        
        RequestInfo.Builder builder = new RequestInfo.Builder();
        // If any timestamps exist, retrieve and preserve them in the returned requestInfo
        RequestInfo requestInfo = cacheProvider.getRequestInfo(session.getId());
        if (requestInfo != null) {
            builder.copyOf(requestInfo);
        }
        builder.withUserId(session.getId());
        builder.withClientInfo(getClientInfoFromUserAgentHeader());
        builder.withUserAgent(request().getHeader(USER_AGENT));
        builder.withLanguages(session.getParticipant().getLanguages());
        builder.withUserDataGroups(session.getParticipant().getDataGroups());
        builder.withTimeZone(session.getParticipant().getTimeZone());
        builder.withStudyIdentifier(session.getStudyIdentifier());
        return builder;
    }

    /**
     * Helper method to add warning message to http header using play framework
     * @param msg
     */
    public static void addWarningMessage(String msg) {
        Http.Response response = Http.Context.current().response();
        if (response.getHeaders().containsKey(BridgeConstants.BRIDGE_API_STATUS_HEADER)) {
            String previousWarning = response.getHeaders().get(BridgeConstants.BRIDGE_API_STATUS_HEADER);
            response.setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, previousWarning + "; " + msg);
        } else {
            response.setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, msg);
        }
    }
}
