package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Service that specifically works for Fitbit API. We may eventually choose an OAuth library if the 
 * implementations vary. 
 */
@Component
class OAuthProviderService {
    private static final Logger LOG = LoggerFactory.getLogger(OAuthProviderService.class);
    
    @FunctionalInterface
    static interface GrantProvider {
        public OAuthAccessGrant grant();
    }
    
    private static final String ACCESS_TOKEN_PROP_NAME = "access_token";
    private static final String AUTHORIZATION_CODE_VALUE = "authorization_code";
    private static final String AUTHORIZATION_PROP_NAME = "Authorization";
    private static final String CLIENT_ID_PROP_NAME = "clientId";
    private static final String CODE_PROP_NAME = "code";
    private static final String CONTENT_TYPE_PROP_NAME = "Content-Type";
    private static final String ERROR_TYPE_PROP_NAME = "errorType";
    private static final String ERRORS_PROP_NAME = "errors";
    private static final String EXPIRES_IN_PROP_NAME = "expires_in";
    private static final String FORM_ENCODING_VALUE = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_PROP_NAME = "grant_type";
    private static final String LOG_ERROR_MSG = "Error retrieving access token, statusCode=%s, body=%s";
    private static final String MESSAGE_PROP_NAME = "message";
    private static final String REDIRECT_URI_PROP_NAME = "redirect_uri";
    private static final String REFRESH_TOKEN_PROP_NAME = "refresh_token";
    private static final String REFRESH_TOKEN_VALUE = "refresh_token";
    private static final String SERVICE_ERROR_MSG = "Error retrieving access token";
    private static final String PROVIDER_USER_ID = "user_id";
    private static final Set<String> INVALID_OR_EXPIRED_ERRORS = Sets.newHashSet("invalid_token", "expired_token", "invalid_grant");
    private static final Set<String> INVALID_CLIENT_ERRORS = Sets.newHashSet("invalid_client");

    /**
     * Simple container for the response, parsed before closing the stream.
     */
    static class Response {
        private final int status;
        private final JsonNode body;
        public Response(int status, JsonNode body) {
            this.status = status;
            this.body = body;
        }
        public int getStatusCode() {
            return this.status;
        }
        public JsonNode getBody() {
            return this.body;
        }
    }

    // accessor so time can be mocked in unit tests
    protected DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }

    // There are separate methods for each HTTP call to enable test mocking
    protected OAuthProviderService.Response executeGrantRequest(HttpPost client) {
        return executeInternal(client);
    }

    protected OAuthProviderService.Response executeRefreshRequest(HttpPost client) {
        return executeInternal(client);
    }

    private OAuthProviderService.Response executeInternal(HttpPost client) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            CloseableHttpResponse response = httpclient.execute(client);
            int statusCode = response.getStatusLine().getStatusCode();
            JsonNode body = BridgeObjectMapper.get().readTree(response.getEntity().getContent());
            return new Response(statusCode, body);
        } catch (IOException e) {
            LOG.error(SERVICE_ERROR_MSG, e);
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        }
    }

    /**
     * Request an access grant token.
     */
    OAuthAccessGrant requestAccessGrant(OAuthProvider provider, OAuthAuthorizationToken authToken) {
        checkNotNull(provider);
        checkNotNull(authToken);

        // If no authorization token has been provided, all we can do is attempt to refresh.
        if (StringUtils.isBlank(authToken.getAuthToken())) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        }
        HttpPost client = createOAuthProviderPost(provider);

        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair(CLIENT_ID_PROP_NAME, provider.getClientId()));
        pairs.add(new BasicNameValuePair(GRANT_TYPE_PROP_NAME, AUTHORIZATION_CODE_VALUE));
        pairs.add(new BasicNameValuePair(REDIRECT_URI_PROP_NAME, provider.getCallbackUrl()));
        pairs.add(new BasicNameValuePair(CODE_PROP_NAME, authToken.getAuthToken()));
        client.setEntity(formEntity(pairs));

        Response response = executeGrantRequest(client);
        
        return handleResponse(response, authToken.getVendorId());
    }
    
    /**
     * Refresh the access grant token.
     */
    OAuthAccessGrant refreshAccessGrant(OAuthProvider provider, String vendorId, String refreshToken) {
        checkNotNull(provider);
        checkNotNull(vendorId);
        
        if (refreshToken == null) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        }
        HttpPost client = createOAuthProviderPost(provider);

        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair(GRANT_TYPE_PROP_NAME, REFRESH_TOKEN_VALUE));
        pairs.add(new BasicNameValuePair(REFRESH_TOKEN_PROP_NAME, refreshToken));
        client.setEntity(formEntity(pairs));

        Response response = executeRefreshRequest(client);
        
        return handleResponse(response, vendorId);
    }
    
    protected HttpPost createOAuthProviderPost(OAuthProvider provider) {
        String authHeader = provider.getClientId() + ":" + provider.getSecret();
        String encodedAuthHeader = "Basic " + Base64.encodeBase64String(authHeader.getBytes());

        HttpPost client = new HttpPost(provider.getEndpoint());
        client.addHeader(AUTHORIZATION_PROP_NAME, encodedAuthHeader);
        client.addHeader(CONTENT_TYPE_PROP_NAME, FORM_ENCODING_VALUE);
        return client;
    }

    protected OAuthAccessGrant handleResponse(Response response, String vendorId) {
        int statusCode = response.getStatusCode();
        
        // Note: this is an interpretation of the errors. It may not be what we finally want, but it was based
        // on initial conversations with client team about what would work for them. For example, returning 401 
        // here may trigger behavior that indicates the user needs to sign in to the client, so we avoid that.
        
        // Invalid client errors indicate that we have not written this service correctly.
        if (statusCode == 401 && isErrorType(response, INVALID_CLIENT_ERRORS)) {
            LOG.error(String.format(LOG_ERROR_MSG, response.getStatusCode(), response.getBody()));
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        } 
        // If it's a 401 (unauthorized) or the tokens are invalid/expired, we report a 404 (no grant).
        else if (statusCode == 401 || isErrorType(response, INVALID_OR_EXPIRED_ERRORS)) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        } 
        // Other 403 exceptions indicate a permissions issue, possibly based on scope or something else
        // that Bridge doesn't control.
        else if (statusCode == 403) {
            throw new UnauthorizedException(jsonToErrorMessage(response.getBody()));
        } 
        // Other bad request, are bad requests... possibly due to input from the client
        else if (statusCode > 399 && statusCode < 500) {
            throw new BadRequestException(jsonToErrorMessage(response.getBody()));
        } 
        // And everything, for now, can be treated as Bridge server error.
        else if (statusCode != 200) {
            LOG.error(String.format(LOG_ERROR_MSG, response.getStatusCode(), response.getBody()));
            throw new BridgeServiceException(SERVICE_ERROR_MSG, response.getStatusCode());
        }
        OAuthAccessGrant grant = jsonToGrant(response.getBody());
        grant.setVendorId(vendorId);
        return grant;
    }

    protected OAuthAccessGrant jsonToGrant(JsonNode node) {
        String accessToken = node.get(ACCESS_TOKEN_PROP_NAME).textValue();
        String refreshToken = node.get(REFRESH_TOKEN_PROP_NAME).textValue();
        String providerUserId = node.get(PROVIDER_USER_ID).textValue();
        int expiresInSeconds = node.get(EXPIRES_IN_PROP_NAME).intValue();

        // Pull expiration back one minute to protect against clock skew between client and server
        DateTime createdOn = getDateTime();
        DateTime expiresOn = createdOn.plusSeconds(expiresInSeconds).minusMinutes(1);
        
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setAccessToken(accessToken);
        grant.setRefreshToken(refreshToken);
        grant.setCreatedOn(createdOn.getMillis());
        grant.setExpiresOn(expiresOn.getMillis());
        grant.setProviderUserId(providerUserId);
        return grant;
    }
    
    protected String jsonToErrorMessage(JsonNode node) {
        List<String> messages = extractFromJSON(node, AbstractMap.SimpleEntry::getValue);
        return BridgeUtils.SPACE_JOINER.join(messages);
    }
    
    private boolean isErrorType(Response response, Set<String> errorTypes) {
        List<String> responseErrorTypes = extractFromJSON(response.getBody(), AbstractMap.SimpleEntry::getKey);
        return !Collections.disjoint(errorTypes, responseErrorTypes);
    }
    
    protected List<String> extractFromJSON(JsonNode node,
            Function<? super SimpleEntry<String, String>, ? extends String> mapField) {
        List<AbstractMap.SimpleEntry<String,String>> list = Lists.newArrayList();
        if (node.has(ERRORS_PROP_NAME)) {
            ArrayNode errors = (ArrayNode) node.get(ERRORS_PROP_NAME);
            errors.forEach((error) -> {
                if (error.has(MESSAGE_PROP_NAME)) {
                    String type = error.get(ERROR_TYPE_PROP_NAME).textValue();
                    String message = error.get(MESSAGE_PROP_NAME).textValue();
                    list.add(new AbstractMap.SimpleEntry<>(type, message));
                }
            });
        }
        return list.stream().map(mapField).collect(Collectors.toList());        
    }

    /**
     * UnsupportedEncodingException is one of those checked exceptions that will *never* be thrown if you use the
     * default system encoding.
     */
    private UrlEncodedFormEntity formEntity(List<NameValuePair> pairs) {
        try {
            return new UrlEncodedFormEntity(pairs);
        } catch (UnsupportedEncodingException e) {
            throw new BridgeServiceException(e);
        }
    }
}
