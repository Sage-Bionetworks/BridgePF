package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.services.OAuthProviderService.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class OAuthProviderServiceTest {

    private static final DateTime NOW = DateTime.now(DateTimeZone.UTC);
    private static final DateTime EXPIRES = NOW.plusSeconds(3600).minusMinutes(1);
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String AUTH_TOKEN_STRING = "authToken";
    private static final String CALLBACK_URL = "callbackUrl";
    private static final String CLIENT_ID = "clientId";
    private static final String ENDPOINT = "endpoint";
    private static final String GRANT_FORM_DATA = "clientId=clientId&grant_type=authorization_code&redirect_uri=callbackUrl&code=authToken";
    private static final String REFRESH_FORM_DATA = "grant_type=refresh_token&refresh_token=refreshToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String REFRESH_TOKEN2 = "refreshToken";
    private static final String SECRET = "secret";
    private static final String USER_ID = "26FWFL";
    private static final String VENDOR_ID = "vendorId";
    private static final OAuthAuthorizationToken AUTH_TOKEN = new OAuthAuthorizationToken(VENDOR_ID, AUTH_TOKEN_STRING);
    private static final OAuthProvider PROVIDER = new OAuthProvider(CLIENT_ID, SECRET, ENDPOINT, CALLBACK_URL);

    @Spy
    private OAuthProviderService service;
    
    @Mock
    private CloseableHttpClient mockClient;
    
    @Captor
    private ArgumentCaptor<HttpPost> grantPostCaptor;
    
    @Captor
    private ArgumentCaptor<HttpPost> refreshPostCaptor;
    
    @Before
    public void before() throws IOException {
        doReturn(NOW).when(service).getDateTime();
    }
    
    private void mockAccessGrantCall(int statusCode, String responseBody) throws IOException {
        String json = TestUtils.createJson(responseBody);
        JsonNode body = BridgeObjectMapper.get().readTree(json);
        doReturn(new Response(statusCode, body)).when(service).executeGrantRequest(grantPostCaptor.capture());
    }

    private void mockRefreshCall(int statusCode, String responseBody) throws IOException {
        String json = TestUtils.createJson(responseBody);
        JsonNode body = BridgeObjectMapper.get().readTree(json);
        doReturn(new Response(statusCode, body)).when(service).executeRefreshRequest(refreshPostCaptor.capture());
    }
    
    private String successJson() {
        return "{'access_token': '"+ACCESS_TOKEN+"',"+
            "'expires_in': 3600,"+
            "'refresh_token': '"+REFRESH_TOKEN+"',"+
            "'token_type': 'Bearer',"+
            "'user_id': '"+USER_ID+"'}";
    }
    private String errorJson(String...strings) {
        List<String> errors = Lists.newArrayList();
        for (int i=0; i < strings.length; i+=2) {
            String type = strings[i];
            String msg = strings[i+1];
            errors.add("{'errorType':'"+type+"', 'message':'"+msg+"'}");
        }
        return "{'errors':["+BridgeUtils.COMMA_JOINER.join(errors)+"],'success':false}";
    }
    
    @Test
    public void makeAccessGrantCall() throws Exception {
        mockAccessGrantCall(200, successJson());
        
        OAuthAccessGrant grant = service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
        
        assertEquals(ACCESS_TOKEN, grant.getAccessToken());
        assertEquals(VENDOR_ID, grant.getVendorId());
        assertEquals(REFRESH_TOKEN2, grant.getRefreshToken());
        assertEquals(NOW.getMillis(), grant.getCreatedOn());
        assertEquals(USER_ID, grant.getProviderUserId());
        assertEquals(EXPIRES.getMillis(), grant.getExpiresOn());
        
        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );
        
        HttpPost thePost = grantPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(authHeader, thePost.getFirstHeader("Authorization").getValue());
        assertEquals("application/x-www-form-urlencoded", thePost.getFirstHeader("Content-Type").getValue());
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(GRANT_FORM_DATA, bodyString);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallWithoutAuthTokenRefreshes() throws Exception {
        OAuthAuthorizationToken emptyPayload = new OAuthAuthorizationToken(VENDOR_ID, null);
        
        service.requestAccessGrant(PROVIDER, emptyPayload);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenMissing() throws Exception {
        OAuthAuthorizationToken emptyPayload = new OAuthAuthorizationToken(VENDOR_ID, null);
        service.requestAccessGrant(PROVIDER, emptyPayload);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenInvalid() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthAndRefreshTokenExpired() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    /**
     * 400s could be due to server code defects, but are most likely going to be the result of invalid 
     * user input that we cannot validate, so preserve the 400 status code.
     */
    @Test
    public void makeAccessGrantCallReturns400() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_request", "Missing parameters: refresh_token.", 
                "invalid_request", "Second error, which seems rare."));
        try {
            service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Missing parameters: refresh_token. Second error, which seems rare.", e.getMessage());
        }
    }
    
    /**
     * 401 specifically signals that an authorization token has expired, and the refresh token should be 
     * used to issue a new access grant.
     */
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallReturns401() throws Exception {
        mockAccessGrantCall(400, errorJson("expired_token", "Access token expired"));
        
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void makeAccessGrantCallReturns500() throws Exception {
        mockAccessGrantCall(500, errorJson());
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void makeAccessGrantCallAuthTokenInvalid() throws Exception {
        mockAccessGrantCall(400, errorJson("invalid_grant", "Authorization code expired: [code]."));
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
  
    @Test(expected = UnauthorizedException.class)
    public void makeRefreshCallAuthorizationError() throws Exception {
        mockAccessGrantCall(403, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        
        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void makeAccessGrantCallIsBadAsProgrammed() throws Exception {
        mockAccessGrantCall(401, errorJson("invalid_client", "Authorization header required."));

        service.requestAccessGrant(PROVIDER, AUTH_TOKEN);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void makeRefreshGrantCallIsBadAsProgrammed() throws Exception {
        mockRefreshCall(401, errorJson("invalid_client", "Authorization header required."));

        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test
    public void refreshAccessCallGrantOK() throws Exception {
        mockRefreshCall(200, successJson());
        
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        
        String authHeader = "Basic " + Base64.encodeBase64String( (CLIENT_ID + ":" + SECRET).getBytes() );
        
        HttpPost thePost = refreshPostCaptor.getValue();
        // Test the headers here... they don't need to be tested in every test, they're always the same.
        assertEquals(authHeader, thePost.getFirstHeader("Authorization").getValue());
        assertEquals("application/x-www-form-urlencoded", thePost.getFirstHeader("Content-Type").getValue());
        String bodyString = EntityUtils.toString(thePost.getEntity());
        assertEquals(REFRESH_FORM_DATA, bodyString);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void refreshAccessCallGrant400Error() throws Exception {
        mockRefreshCall(400, errorJson("invalid_token", "Authorization code expired: [code]."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void refreshAccessCallGrant403Error() throws Exception {
        mockRefreshCall(403, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void refreshAccessCallGrant500Error() throws Exception {
        mockRefreshCall(500, errorJson("insufficient_scope",
                "This application does not have permission to [access-type] [resource-type] data."));
        service.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
    }
}

