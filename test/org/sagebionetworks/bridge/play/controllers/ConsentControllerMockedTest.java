package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.sagebionetworks.bridge.TestUtils.createJson;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
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
    @Captor
    ArgumentCaptor<Consumer<Account>> accountConsumerCaptor;
    
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
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq(HEALTH_CODE), accountConsumerCaptor.capture());
        verify(sessionUpdateService).updateSharingScope(session, SharingScope.ALL_QUALIFIED_RESEARCHERS);
        
        // This works as a verification because the lambda carries a closure that includes the correct sharing 
        // scope... so re-executing it should do what we expect and set the correct sharing scope.
        Consumer<Account> accountConsumer = accountConsumerCaptor.getValue();
        Account account = Mockito.mock(Account.class);
        accountConsumer.accept(account);
        verify(account).setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void changeSharingScopeNoPayload() throws Exception {
        TestUtils.mockPlay().mock();
        controller.changeSharingScope();
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getConsentSignatureV1() throws Exception {
        SubpopulationGuid defaultGuid = SubpopulationGuid.create(TestConstants.TEST_STUDY_IDENTIFIER);
        when(consentService.getConsentSignature(study, defaultGuid, USER_ID)).thenReturn(SIGNATURE);
        
        Result result = controller.getConsentSignature();
        
        assertEquals(200, result.status());
        ConsentSignature retrieved = TestUtils.getResponsePayload(result, ConsentSignature.class);
        assertEquals(SIGNATURE.getName(), retrieved.getName());
        assertEquals(SIGNATURE.getBirthdate(), retrieved.getBirthdate());
        
        verify(consentService).getConsentSignature(study, defaultGuid, USER_ID);
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
    
    @SuppressWarnings("deprecation")
    @Test
    public void giveV1() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing'}");
        TestUtils.mockPlay().withJsonBody(json).withMockResponse().mock();
        
        StudyIdentifier studyId = new StudyIdentifierImpl(TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid());
        
        // Need to adjust the study session to match the subpopulation in the unconsented status map
        session.setStudyIdentifier(studyId);
        when(authenticationService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        when(consentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        when(studyService.getStudy(studyId)).thenReturn(study);
        
        Result result = controller.giveV1();
        
        assertEquals(201, result.status());
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        assertEquals(UPDATED_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(consentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(cacheProvider).setUserSession(updatedSession);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void giveV2() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing'}");
        TestUtils.mockPlay().withJsonBody(json).withMockResponse().mock();
        
        StudyIdentifier studyId = new StudyIdentifierImpl(TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid());
        
        // Need to adjust the study session to match the subpopulation in the unconsented status map
        session.setStudyIdentifier(studyId);
        when(authenticationService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        when(consentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        when(studyService.getStudy(studyId)).thenReturn(study);
        
        Result result = controller.giveV2();
        
        assertEquals(201, result.status());
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        assertEquals(UPDATED_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(consentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(cacheProvider).setUserSession(updatedSession);
    }
    
    @Test
    public void giveV3() throws Exception {
        DateTime badSignedOn = new DateTime(UNIX_TIMESTAMP).minusHours(1);
        
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing',"+
                "'signedOn': '" + badSignedOn.toString() + "'}");
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
    
    @Test(expected = EntityNotFoundException.class)
    public void givingConsentToInvalidSubpopulation() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10','imageData':'data:asdf',"+
                "'imageMimeType':'image/png','scope':'no_sharing'}");
        TestUtils.mockPlay().withMockResponse().withJsonBody(json).mock();
        doReturn(session).when(controller).getAuthenticatedSession();
        
        // It will not find the correct subpopulation.
        when(consentService.getConsentStatuses(any())).thenReturn(ImmutableMap.of());
        
        controller.giveV3("bad-guid");
    }    
    
    @SuppressWarnings("deprecation")
    @Test
    public void withdrawConsent() throws Exception {
        SubpopulationGuid defaultGuid = SubpopulationGuid.create(TestConstants.TEST_STUDY_IDENTIFIER);
        TestUtils.mockPlay().withMockResponse().withBody(WITHDRAWAL).mock();
        
        // You do not need to be fully consented for this call to succeed. Nothing should prevent
        // this call from succeeding unless it's absolutely necessary (see BRIDGE-2418 about 
        // removing the requirement that a withdrawal reason be submitted).
        doReturn(session).when(controller).getAuthenticatedSession();
        when(authenticationService.getSession(any(), any())).thenReturn(updatedSession);
        session.setConsentStatuses(TestConstants.UNCONSENTED_STATUS_MAP);

        Result result = controller.withdrawConsent();
        
        assertEquals(200, result.status());
        UserSession retrievedSession = TestUtils.getResponsePayload(result, UserSession.class);
        // This call recreates the session from scratch
        assertEquals(UPDATED_SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(consentService).withdrawConsent(eq(study), eq(defaultGuid), eq(session.getParticipant()), any(),
                eq(WITHDRAWAL), eq(UNIX_TIMESTAMP));
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
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingSuspendedUpdatesSession() throws Exception {
        Account account = Mockito.mock(Account.class);
        TestUtils.mockEditAccount(accountDao, account);
        
        doAnswer((InvocationOnMock invocation) -> {
            session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                    .withSharingScope(SharingScope.NO_SHARING).build());
            return null;
        }).when(sessionUpdateService).updateSharingScope(session, SharingScope.NO_SHARING);

        Result result = controller.suspendDataSharing();

        TestUtils.assertResult(result, 200);
        JsonNode node = TestUtils.getJson(result);
        assertEquals("no_sharing", node.get("sharingScope").asText());
        assertFalse(node.get("dataSharing").asBoolean());

        verify(account).setSharingScope(SharingScope.NO_SHARING);
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq(HEALTH_CODE),
                accountConsumerCaptor.capture());
        
        Consumer<Account> accountConsumer = accountConsumerCaptor.getValue();
        Account updatedAccount = Account.create();
        accountConsumer.accept(updatedAccount);
        assertEquals(SharingScope.NO_SHARING, updatedAccount.getSharingScope());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingResumedUpdatesSession() throws Exception {
        Account account = Mockito.mock(Account.class);
        TestUtils.mockEditAccount(accountDao, account);

        doAnswer((InvocationOnMock invocation) -> {
            session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                    .withSharingScope(SharingScope.SPONSORS_AND_PARTNERS).build());
            return null;
        }).when(sessionUpdateService).updateSharingScope(session, SharingScope.SPONSORS_AND_PARTNERS);
        
        Result result = controller.resumeDataSharing();

        TestUtils.assertResult(result, 200);
        JsonNode node = TestUtils.getJson(result);
        assertEquals("sponsors_and_partners", node.get("sharingScope").asText());
        assertTrue(node.get("dataSharing").asBoolean());
        
        verify(account).setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
    }    
}
