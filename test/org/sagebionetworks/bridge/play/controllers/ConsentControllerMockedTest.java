package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.sagebionetworks.bridge.TestUtils.createJson;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http.Response;
import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class ConsentControllerMockedTest {

    private static final String UPDATED_SESSION_TOKEN = "updated-session-token";
    private static final String ORIGINAL_SESSION_TOKEN = "original-session-token";
    private static final String USER_ID = "userId";
    private static final String HEALTH_CODE = "healthCode";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid());
    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    private static final ConsentSignature SIGNATURE = new ConsentSignature.Builder().withName("Jack Aubrey")
            .withBirthdate("1970-10-10").withImageData("data:asdf").withImageMimeType("image/png")
            .withSignedOn(UNIX_TIMESTAMP).build();
    private static final Withdrawal WITHDRAWAL = new Withdrawal("reasons");
    
    private UserSession session;
    private Study study;
    
    private UserSession updatedSession;
    
    @Spy
    private ConsentController controller;

    @Mock
    private StudyService studyService;
    @Mock
    private ConsentService consentService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private SessionUpdateService sessionUpdateService;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(UNIX_TIMESTAMP);
        
        controller.setAccountDao(accountDao);
        controller.setSessionUpdateService(sessionUpdateService);
        controller.setStudyService(studyService);
        controller.setConsentService(consentService);
        controller.setAuthenticationService(authenticationService);
        controller.setCacheProvider(cacheProvider);
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).withId(USER_ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        session.setSessionToken(ORIGINAL_SESSION_TOKEN);
        
        // The session token is just a marker to verify that we have retrieved an updated session.
        updatedSession = new UserSession();
        updatedSession.setSessionToken(UPDATED_SESSION_TOKEN);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void changeSharingScope() throws Exception {
        TestUtils.mockPlay().withJsonBody("{\"scope\":\"all_qualified_researchers\"}").mock();
        
        Result result = controller.changeSharingScope();
        
        assertEquals(200, result.status());
        // Session is edited by sessionUpdateService, not reloaded
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        assertEquals(ORIGINAL_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq(HEALTH_CODE), any());
        verify(sessionUpdateService).updateSharingScope(session, SharingScope.ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void changeSharingScopeNoPayload() throws Exception {
        TestUtils.mockPlay().mock();
        controller.changeSharingScope();
    }
    
    @Test
    public void getConsentSignatureV2() throws Exception {
        when(consentService.getConsentSignature(study, SUBPOP_GUID, USER_ID)).thenReturn(SIGNATURE);
        
        Result result = controller.getConsentSignatureV2(SUBPOP_GUID.getGuid());
        
        assertEquals(200, result.status());
        ConsentSignature retrieved = TestUtils.getResponsePayload(result, ConsentSignature.class);
        assertEquals(SIGNATURE.getName(), retrieved.getName());
        assertEquals(SIGNATURE.getBirthdate(), retrieved.getBirthdate());
        
        verify(consentService).getConsentSignature(study, SUBPOP_GUID, USER_ID);
    }
    
    @Test
    public void giveV3() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing'}");
        TestUtils.mockPlay().withJsonBody(json).withMockResponse().mock();
        
        when(authenticationService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        
        when(consentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        assertEquals(201, result.status());
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        assertEquals(UPDATED_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(consentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(cacheProvider).setUserSession(updatedSession);
    }
    
    @Test
    public void withdrawConsentV2() throws Exception {
        TestUtils.mockPlay().withMockResponse().withBody(WITHDRAWAL).mock();
        
        // You do not need to be fully consented for this call to succeed. Nothing should prevent
        // this call from succeeding unless it's absolutely necessary (see BRIDGE-2418 about 
        // removing the requirement that a withdrawal reason be submitted).
        doReturn(session).when(controller).getAuthenticatedSession();
        when(authenticationService.getSession(any(), any())).thenReturn(updatedSession);
        session.setConsentStatuses(TestConstants.UNCONSENTED_STATUS_MAP);

        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        assertEquals(200, result.status());
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        // This call recreates the session from scratch
        assertEquals(UPDATED_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(consentService).withdrawConsent(eq(study), eq(SUBPOP_GUID), eq(session.getParticipant()), any(),
                eq(WITHDRAWAL), eq(UNIX_TIMESTAMP));
        verify(cacheProvider).setUserSession(updatedSession);
    }
    
    @Test
    public void withdrawFromStudy() throws Exception {
        Response mockResponse = TestUtils.mockPlay().withMockResponse().withBody(WITHDRAWAL).mock();
        // You do not need to be fully consented for this call to succeed.
        doReturn(session).when(controller).getAuthenticatedSession();
        
        Result result = controller.withdrawFromStudy();
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getResponsePayload(result, JsonNode.class);
        assertEquals("Signed out.", node.get("message").textValue());
        
        verify(consentService).withdrawFromStudy(study, session.getParticipant(), WITHDRAWAL, UNIX_TIMESTAMP);
        verify(authenticationService).signOut(session);
        verify(mockResponse).discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
    }

    @Test
    public void resendConsentAgreement() throws Exception {
        Result result = controller.resendConsentAgreement(SUBPOP_GUID.getGuid());
        
        assertEquals(202, result.status());
        JsonNode node = TestUtils.getResponsePayload(result, JsonNode.class);
        assertEquals("Signed consent agreement resent.", node.get("message").textValue());
        
        verify(consentService).resendConsentAgreement(study, SUBPOP_GUID, session.getParticipant());        
    }    
}
