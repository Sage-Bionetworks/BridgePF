package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.OAuthService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.newrelic.agent.deps.com.google.common.collect.Maps;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class OAuthControllerTest {
    
    private static final String NEXT_PAGE_OFFSET_KEY = "offsetKey2";
    private static final String OFFSET_KEY = "offsetKey";
    private static final String AUTH_TOKEN = "authToken";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String HEALTH_CODE = "healthCode";
    private static final String VENDOR_ID = "vendorId";
    private static final String PROVIDER_USER_ID = "providerUserId";
    private static final List<String> HEALTH_CODE_LIST = Lists.newArrayList("a", "b", "c");
    // Set an offset so we can verify it exists.
    private static final DateTime EXPIRES_ON = DateTime.parse("2017-11-28T14:20:22.123-03:00");
    
    @Spy
    private OAuthController controller;
    
    @Mock
    private OAuthService mockOauthService;
    
    @Mock
    private Study mockStudy;
    
    @Mock
    private StudyService mockStudyService;
    
    @Captor
    private ArgumentCaptor<OAuthAuthorizationToken> authTokenCaptor;
    
    private UserSession session; 
    
    @Before
    public void before() {
        controller.setOAuthService(mockOauthService);
        controller.setStudyService(mockStudyService);
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(mockStudy);
        when(mockStudyService.getStudy(TEST_STUDY.getIdentifier())).thenReturn(mockStudy);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void requestAccessTokenMustBeAuthenticated() {
        session.setAuthenticated(false);
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.requestAccessToken(VENDOR_ID);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void requestAccessTokenMustBeConsented() {
        session.setAuthenticated(true);
        
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create("ABC"), TestConstants.REQUIRED_UNSIGNED);
        session.setConsentStatuses(statuses);
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.requestAccessToken(VENDOR_ID);
    }
    
    @Test
    public void requestAccessTokenWithAccessToken() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        OAuthAuthorizationToken authToken = new OAuthAuthorizationToken(null, AUTH_TOKEN);
        TestUtils.mockPlayContextWithJson(authToken);
        
        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID);
        when(mockOauthService.requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), any()))
                .thenReturn(accessToken);
        
        Result result = controller.requestAccessToken(VENDOR_ID);
        TestUtils.assertResult(result, 200);
        
        OAuthAccessToken returned = TestUtils.getResponsePayload(result, OAuthAccessToken.class);
        assertEquals(accessToken, returned);
        
        verify(mockOauthService).requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), authTokenCaptor.capture());
        OAuthAuthorizationToken captured = authTokenCaptor.getValue();
        assertEquals(AUTH_TOKEN, captured.getAuthToken());
        assertEquals(VENDOR_ID, captured.getVendorId());
    }
    
    @Test
    public void requestAccessTokenWithoutAccessToken() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        TestUtils.mockPlayContextWithJson("{}");
        
        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID);
        when(mockOauthService.requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), any()))
                .thenReturn(accessToken);
        
        Result result = controller.requestAccessToken(VENDOR_ID);
        TestUtils.assertResult(result, 200);
        
        OAuthAccessToken returned = TestUtils.getResponsePayload(result, OAuthAccessToken.class);
        assertEquals(accessToken, returned);
        // verify that the time zone is preserved
        assertEquals("2017-11-28T14:20:22.123-03:00", returned.getExpiresOn().toString());
        
        verify(mockOauthService).requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), authTokenCaptor.capture());
        OAuthAuthorizationToken captured = authTokenCaptor.getValue();
        assertNull(captured.getAuthToken());
        assertEquals(VENDOR_ID, captured.getVendorId());
    }

    @Test
    public void getHealthCodesGrantingAccessWithDefaults() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(Roles.WORKER);
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(HEALTH_CODE_LIST,
                NEXT_PAGE_OFFSET_KEY);
        when(mockOauthService.getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                null)).thenReturn(page);
        
        Result result = controller.getHealthCodesGrantingAccess(TEST_STUDY_IDENTIFIER, VENDOR_ID, null, null);
        TestUtils.assertResult(result, 200);
        
        verify(mockOauthService).getHealthCodesGrantingAccess(mockStudy, VENDOR_ID,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
        
        ForwardCursorPagedResourceList<String> returned = TestUtils.getResponsePayload(result,
                new TypeReference<ForwardCursorPagedResourceList<String>>() {});
        assertEquals(HEALTH_CODE_LIST, returned.getItems());
        assertEquals(NEXT_PAGE_OFFSET_KEY, returned.getNextPageOffsetKey());
        assertNull(returned.getRequestParams().get(OFFSET_KEY));
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getHealthCodesGrantingAccessRequiresWorker() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.getHealthCodesGrantingAccess(TEST_STUDY_IDENTIFIER, VENDOR_ID, OFFSET_KEY, "20");
    }
    
    @Test
    public void getHealthCodesGrantingAccess() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(Roles.WORKER);
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(HEALTH_CODE_LIST,
                NEXT_PAGE_OFFSET_KEY).withRequestParam(OFFSET_KEY, OFFSET_KEY);
        when(mockOauthService.getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, 20, OFFSET_KEY)).thenReturn(page);
        
        Result result = controller.getHealthCodesGrantingAccess(TEST_STUDY_IDENTIFIER, VENDOR_ID, OFFSET_KEY, "20");
        TestUtils.assertResult(result, 200);
        
        verify(mockOauthService).getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, 20, OFFSET_KEY);
        
        ForwardCursorPagedResourceList<String> returned = TestUtils.getResponsePayload(result,
                new TypeReference<ForwardCursorPagedResourceList<String>>() {});
        assertEquals(HEALTH_CODE_LIST, returned.getItems());
        assertEquals(NEXT_PAGE_OFFSET_KEY, returned.getNextPageOffsetKey());
        assertEquals(OFFSET_KEY, returned.getRequestParams().get(OFFSET_KEY));
    }
    
    
    @Test(expected = NotAuthenticatedException.class)
    public void getAccessTokenRequiresWorker() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.getAccessToken(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test
    public void getAccessToken() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(Roles.WORKER);

        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID);
        
        when(mockOauthService.getAccessToken(mockStudy, VENDOR_ID, HEALTH_CODE)).thenReturn(accessToken);
        
        Result result = controller.getAccessToken(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        TestUtils.assertResult(result, 200);
        
        OAuthAccessToken returned = TestUtils.getResponsePayload(result, OAuthAccessToken.class);
        assertEquals(accessToken, returned);
        // verify that the time zone is preserved
        assertEquals("2017-11-28T14:20:22.123-03:00", returned.getExpiresOn().toString());
        
        verify(mockOauthService).getAccessToken(mockStudy, VENDOR_ID, HEALTH_CODE);
    }
}
