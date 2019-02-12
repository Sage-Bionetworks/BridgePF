package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.OAuthAccessGrantDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class OAuthServiceTest {

    private static final DateTime NOW = DateTime.now(DateTimeZone.UTC);
    private static final DateTime EXPIRES_ON = NOW.plusHours(3);
    private static final String HEALTH_CODE = "healthCode";
    private static final String VENDOR_ID = "vendorId";
    private static final String AUTH_TOKEN_STRING = "authToken";
    private static final String CLIENT_ID = "clientId";
    private static final String SECRET = "secret";
    private static final String ENDPOINT = "endpoint";
    private static final String CALLBACK_URL = "callbackUrl";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final OAuthProvider PROVIDER = new OAuthProvider(CLIENT_ID, SECRET, ENDPOINT, CALLBACK_URL);
    private static final OAuthAuthorizationToken AUTH_TOKEN = new OAuthAuthorizationToken(VENDOR_ID, AUTH_TOKEN_STRING);
    private static final OAuthAuthorizationToken NO_AUTH_TOKEN = new OAuthAuthorizationToken(VENDOR_ID, null);
    
    @Spy
    private OAuthService service;
    
    @Mock
    private OAuthAccessGrantDao mockGrantDao;
    
    @Mock
    private OAuthProviderService mockProviderService;
    
    @Captor
    private ArgumentCaptor<OAuthAccessGrant> grantCaptor;
    
    private Study study;
    
    @Before
    public void before() {
        service.setOAuthAccessGrantDao(mockGrantDao);
        service.setOAuthProviderService(mockProviderService);
        
        Map<String,OAuthProvider> providers = new HashMap<String,OAuthProvider>();
        providers.put("vendorId", PROVIDER);
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY.getIdentifier());
        study.setOAuthProviders(providers);
        
        when(service.getDateTime()).thenReturn(NOW);
    }
    
    private OAuthAccessGrant createGrant(DateTime expiresOn) {
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setCreatedOn(NOW.getMillis());
        grant.setExpiresOn(expiresOn.getMillis());
        grant.setAccessToken(ACCESS_TOKEN);
        grant.setVendorId(VENDOR_ID);
        grant.setRefreshToken(REFRESH_TOKEN);
        return grant;
    }
    
    private void setupDaoWithCurrentGrant() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockGrantDao.getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE)).thenReturn(grant);
    }
    private void setupDaoWithExpiredGrant() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON.minusHours(4));
        when(mockGrantDao.getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE)).thenReturn(grant);
    }
    
    private void setupValidGrantCall() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockProviderService.requestAccessGrant(PROVIDER, AUTH_TOKEN)).thenReturn(grant);
    }
    private void setupInvalidGrantCall() {
        setupInvalidGrantCall(new EntityNotFoundException(OAuthAccessGrant.class));
    }
    private void setupInvalidGrantCall(BridgeServiceException e) {
        when(mockProviderService.requestAccessGrant(PROVIDER, AUTH_TOKEN))
                .thenThrow(e);
    }
    
    private void setupValidRefreshCall() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockProviderService.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN)).thenReturn(grant);
    }
    private void setupInvalidRefreshCall() {
        when(mockProviderService.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN))
                .thenThrow(new EntityNotFoundException(OAuthAccessGrant.class));
    }
    
    private void assertAccessToken(OAuthAccessToken authToken) {
        assertEquals(VENDOR_ID, authToken.getVendorId());
        assertEquals(ACCESS_TOKEN, authToken.getAccessToken());
        assertEquals(EXPIRES_ON, authToken.getExpiresOn());
    }
    private void assertGrant(OAuthAccessGrant grant) {
        assertEquals(HEALTH_CODE, grant.getHealthCode());
        assertEquals(VENDOR_ID, grant.getVendorId());
        assertEquals(ACCESS_TOKEN, grant.getAccessToken());
        assertEquals(REFRESH_TOKEN, grant.getRefreshToken());
        assertEquals(NOW.getMillis(), grant.getCreatedOn());
        assertEquals(EXPIRES_ON.getMillis(), grant.getExpiresOn());
    }
    
    // All of these tests are for requestAccessToken(...)
    
    @Test
    public void requestCurrentGrant() {
        setupDaoWithCurrentGrant();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TestConstants.TEST_STUDY), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void requestExpiredGrantValidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupValidRefreshCall();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verify(mockGrantDao).saveAccessGrant(eq(TestConstants.TEST_STUDY), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void requestExpiredGrantInvalidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupInvalidRefreshCall();
        
        try {
            service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        verify(mockGrantDao).getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verify(mockGrantDao).deleteAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }
    
    @Test
    public void requestNoGrantValidAuthToken() {
        setupValidGrantCall();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verify(mockGrantDao).saveAccessGrant(eq(TestConstants.TEST_STUDY), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getHealthCodesThrowsExceptionOnIncorrectOAuthProvider() {
        service.getHealthCodesGrantingAccess(study, "notAVendor", 50, null);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getHealthCodesThrowsExceptionOnIncorrectOAuthProvider2() {
        service.getHealthCodesGrantingAccess(study, "notAVendor", 50, null);
    }
    
    @Test
    public void requestNoGrantInvalidAuthToken() {
        setupInvalidGrantCall();
        try {
            service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verify(mockGrantDao).deleteAccessGrant(study.getStudyIdentifier(), VENDOR_ID, HEALTH_CODE);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }

    // getAccessToken tests
    
    @Test
    public void getCurrentGrant() {
        setupDaoWithCurrentGrant();
        
        OAuthAccessToken token = service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TestConstants.TEST_STUDY), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void getExpiredGrantValidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupValidRefreshCall();

        OAuthAccessToken token = service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TestConstants.TEST_STUDY, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TestConstants.TEST_STUDY), grantCaptor.capture());
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getExpiredGrantInvalidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupInvalidRefreshCall();

        service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getNoGrant() {
        service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test
    public void getHealthCodesGrantingAccess() {
        List<OAuthAccessGrant> items = Lists.newArrayList();
        items.add(OAuthAccessGrant.create());
        items.add(OAuthAccessGrant.create());
        ForwardCursorPagedResourceList<OAuthAccessGrant> page = new ForwardCursorPagedResourceList<>(items, "nextPageOffset")
                .withRequestParam("offsetKey", "offsetKey");
        
        when(mockGrantDao.getAccessGrants(study, VENDOR_ID, "offsetKey", 30)).thenReturn(page);
        
        ForwardCursorPagedResourceList<String> results = service.getHealthCodesGrantingAccess(study, VENDOR_ID, 30,
                "offsetKey");
        
        verify(mockGrantDao).getAccessGrants(study, VENDOR_ID, "offsetKey", 30);
        // Just verify a couple of fields to verify this is the page returned
        assertEquals("nextPageOffset", results.getNextPageOffsetKey());
        assertEquals(2, results.getItems().size());
    }
    
    @Test
    public void transientProviderErrorDoesNotDeleteGrant() {
        setupInvalidGrantCall(new BridgeServiceException("Temporary error", 503));
        
        try {
            service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            
        }
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }
}
