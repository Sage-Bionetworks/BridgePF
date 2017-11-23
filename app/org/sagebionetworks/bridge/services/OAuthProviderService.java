package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * So far, this has been straightforward enough to skip a dedicated OAuth library. 
 */
public class OAuthProviderService {
    private static final Logger LOG = LoggerFactory.getLogger(OAuthProviderService.class);
    
    @FunctionalInterface
    public static interface GrantProvider {
        public OAuthAccessGrant grant();
    }
    
    private static final String MESSAGE_PROP_NAME = "message";
    private static final String ERROR_TYPE_PROP_NAME = "errorType";
    private static final String ERRORS_PROP_NAME = "errors";
    private static final String CONTENT_TYPE_PROP_NAME = "Content-Type";
    private static final String AUTHORIZATION_PROP_NAME = "Authorization";
    private static final String REFRESH_TOKEN_PROP_NAME = "refresh_token";
    private static final String REDIRECT_URI_PROP_NAME = "redirect_uri";
    private static final String CODE_PROP_NAME = "code";
    private static final String GRANT_TYPE_PROP_NAME = "grant_type";
    private static final String CLIENT_ID_PROP_NAME = "clientId";
    private static final String EXPIRES_IN_PROP_NAME = "expires_in";
    private static final String ACCESS_TOKEN_PROP_NAME = "access_token";
    
    private static final String REFRESH_TOKEN_VALUE = "refresh_token";
    private static final String FORM_ENCODING_VALUE = "application/x-www-form-urlencoded";
    private static final String AUTHORIZATION_CODE_VALUE = "authorization_code";

    private static final String SERVICE_ERROR_MSG = "Error retrieving access token";
    private static final String LOG_ERROR_MSG = "Error retrieving access token, statusCode=%s, body=%s";
    
    private static final Set<String> INVALID_OR_EXPIRED_ERRORS = Sets.newHashSet("invalid_token", "expired_token", "invalid_grant");

    // Simple container for the response, parsed before closing the stream
    public static class Response {
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

    protected DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }

    // There are separate methods for each call to enable test mocking

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
            JsonNode body = responseToJSON(response);
            return new Response(statusCode, body);
        } catch (IOException e) {
            LOG.error(SERVICE_ERROR_MSG, e);
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        }
    }

    public OAuthAccessGrant makeAccessGrantCall(OAuthProvider provider, OAuthAuthorizationToken authToken) {
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
     * In cases where the expiration clearly indicates the grant has expired, you can attempt to refresh directly. If
     * you were to call the access grant call, it would determine the same state, and make the refresh call in two
     * requests.
     */
    public OAuthAccessGrant makeRefreshAccessGrantCall(OAuthProvider provider, String vendorId, String refreshToken) {
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

        if (statusCode == 401 || isInvalidOrExpired(response)) {
            throw new EntityNotFoundException(OAuthAccessGrant.class);
        } else if (statusCode == 403) {
            throw new UnauthorizedException(jsonToErrorMessage(response.getBody()));
        } else if (statusCode > 399 && statusCode < 500) {
            throw new BadRequestException(jsonToErrorMessage(response.getBody()));
        } else if (statusCode != 200) {
            LOG.error(String.format(LOG_ERROR_MSG, response.getStatusCode(), response.getBody()));
            throw new BridgeServiceException(SERVICE_ERROR_MSG);
        }

        OAuthAccessGrant grant = jsonToGrant(response.getBody());
        grant.setVendorId(vendorId);
        return grant;
    }

    protected OAuthAccessGrant jsonToGrant(JsonNode node) {
        String accessToken = node.get(ACCESS_TOKEN_PROP_NAME).textValue();
        String refreshToken = node.get(REFRESH_TOKEN_PROP_NAME).textValue();
        int expiresInSeconds = node.get(EXPIRES_IN_PROP_NAME).intValue();

        // Pull expiration back one minute to protect against running over expiration time
        DateTime createdOn = getDateTime();
        DateTime expiresOn = createdOn.plusSeconds(expiresInSeconds).minusMinutes(1);

        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setAccessToken(accessToken);
        grant.setRefreshToken(refreshToken);
        grant.setCreatedOn(createdOn.getMillis());
        grant.setExpiresOn(expiresOn.getMillis());
        return grant;
    }

    protected JsonNode responseToJSON(CloseableHttpResponse response) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = reader.readLine()) != null) {
            sb.append(output);
        }
        return BridgeObjectMapper.get().readTree(sb.toString());
    }

    protected String jsonToErrorMessage(JsonNode node) {
        if (node.has(ERRORS_PROP_NAME)) {
            ArrayNode errors = (ArrayNode) node.get(ERRORS_PROP_NAME);
            List<String> errorMessages = Lists.newArrayListWithCapacity(errors.size());
            for (int i = 0; i < errors.size(); i++) {
                JsonNode error = errors.get(i);
                if (error.has(MESSAGE_PROP_NAME)) {
                    String message = error.get(MESSAGE_PROP_NAME).textValue();
                    errorMessages.add(message);
                }
            }
            return BridgeUtils.SPACE_JOINER.join(errorMessages);
        }
        // No error messages? Something is odd here.
        return "Error JSON not returned from OAuth provider";
    }

    private boolean isInvalidOrExpired(Response response) {
        if (response.getStatusCode() != 200) {
            JsonNode node = response.getBody();
            if (node.has(ERRORS_PROP_NAME)) {
                ArrayNode errors = (ArrayNode) node.get(ERRORS_PROP_NAME);
                for (int i = 0; i < errors.size(); i++) {
                    JsonNode error = errors.get(i);
                    if (error.has(ERROR_TYPE_PROP_NAME)) {
                        String type = error.get(ERROR_TYPE_PROP_NAME).textValue();
                        if (INVALID_OR_EXPIRED_ERRORS.contains(type)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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
