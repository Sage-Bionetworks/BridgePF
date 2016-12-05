package org.sagebionetworks.bridge.play.controllers;

import static org.apache.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.BridgeConstants.*;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.newLinkedHashSet;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.springframework.test.context.ContextConfiguration;
import play.libs.ws.WS;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Http;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.annotation.Resource;

/** Test class for basic utility functions in BaseController. */
@SuppressWarnings("unchecked")
public class BaseControllerTest {

    private static final String DUMMY_JSON = createJson("{'dummy-key':'dummy-value'}");
    private static final LinkedHashSet<String> LANGUAGE_SET = newLinkedHashSet("en","fr");

    @Test
    public void testParseJsonFromText() {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(DUMMY_JSON);

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        Map<String, String> resultMap = BaseController.parseJson(mockRequest, Map.class);
        assertEquals(1, resultMap.size());
        assertEquals("dummy-value", resultMap.get("dummy-key"));
    }

    @Test
    public void testParseJsonFromNode() throws Exception {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(null);
        when(mockBody.asJson()).thenReturn(BridgeObjectMapper.get().readTree(DUMMY_JSON));

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        Map<String, String> resultMap = BaseController.parseJson(mockRequest, Map.class);
        assertEquals(1, resultMap.size());
        assertEquals("dummy-value", resultMap.get("dummy-key"));
    }

    @Test(expected = InvalidEntityException.class)
    public void testParseJsonError() {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenThrow(RuntimeException.class);
        BaseController.parseJson(mockRequest, Map.class);
    }

    @Test(expected = InvalidEntityException.class)
    public void testParseJsonNoJson() throws Exception {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(null);
        when(mockBody.asJson()).thenReturn(null);

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        BaseController.parseJson(mockRequest, Map.class);
    }
    
    @Test
    public void canRetrieveClientInfoObject() throws Exception {
        mockHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals(OperatingSystem.IOS, info.getOsName());
        assertEquals("9.0.2", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
    @Test
    public void doesNotThrowErrorWhenUserAgentStringInvalid() throws Exception {
        mockHeader(USER_AGENT, 
                "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi");
        
        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();
        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }

    @Test
    public void setWarningHeaderWhenNoUserAgent() throws Exception {
        mockPlayContext();

        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, BRIDGE_WARNING_STATUS);

        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }

    @Test (expected = UnsupportedVersionException.class)
    public void testInvalidSupportedVersionThrowsException() throws Exception {
        mockHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 28);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);

    }
    
    @Test
    public void testValidSupportedVersionDoesNotThrowException() throws Exception {
        mockHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testNullSupportedVersionDoesNotThrowException() throws Exception {
        mockHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testUnknownOSDoesNotThrowException() throws Exception {
        mockHeader(USER_AGENT, "Asthma/26 BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void roleEnforcedWhenRetrievingSession() throws Exception {
        mockPlayContext();
        
        SchedulePlanController controller = spy(new SchedulePlanController());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        doReturn(session).when(controller).getSessionIfItExists();

        // Single arg success.
        assertNotNull(controller.getAuthenticatedSession(Roles.RESEARCHER));

        // This method, upon confronting the fact that the user does not have this role, 
        // throws an UnauthorizedException.
        try {
            controller.getAuthenticatedSession(Roles.ADMIN);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            // expected exception
        }

        // Success with sets.
        assertNotNull(controller.getAuthenticatedSession(Roles.RESEARCHER));
        assertNotNull(controller.getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER));
        assertNotNull(controller.getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER, Roles.WORKER));

        // Unauthorized with sets
        try {
            controller.getAuthenticatedSession(Roles.ADMIN, Roles.DEVELOPER, Roles.WORKER);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            // expected exception
        }
    }
    
    @Test
    public void canRetrieveLanguagesFromAcceptHeader() throws Exception {
        BaseController controller = new SchedulePlanController();
        
        mockPlayContext();
        
        // with no accept language header at all, things don't break;
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        // testing this because the rest of these tests will use ImmutableSet.of()
        assertTrue(langs instanceof LinkedHashSet); 
        assertEquals(ImmutableSet.of(), langs);

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, BRIDGE_WARNING_STATUS);
        
        mockHeader(ACCEPT_LANGUAGE, "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
        
        langs = controller.getLanguagesFromAcceptLanguageHeader();
            
        LinkedHashSet<String> set = newLinkedHashSet("en","de");
        assertEquals(set, langs);

        mockHeader(ACCEPT_LANGUAGE, null);
        langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableSet.of(), langs);
            
        mockHeader(ACCEPT_LANGUAGE, "");
        langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableSet.of(), langs);
            
        mockHeader(ACCEPT_LANGUAGE, "en-US");
        langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableSet.of("en"), langs);
            
        mockHeader(ACCEPT_LANGUAGE, "FR,en-US");
        langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableSet.of("fr","en"), langs);
        
        // Real header from Chrome... works fine
        mockHeader(ACCEPT_LANGUAGE, "en-US,en;q=0.8");
        langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableSet.of("en"), langs);
    }
    
    // We don't want to throw a BadRequestException due to a malformed header. Just return no languages.
    @Test
    public void badAcceptLanguageHeaderSilentlyIgnored() throws Exception {
        BaseController controller = new SchedulePlanController();
        
        mockPlayContext();
        // This is apparently a bad User-Agent header some browser is sending to us; any failure will do though.
        mockHeader(ACCEPT_LANGUAGE, "chrome://global/locale/intl.properties");
        
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertTrue(langs.isEmpty());
    }
    
    @Test
    public void canGetLanguagesWhenInSession() {
        BaseController controller = new SchedulePlanController();
        
        StudyParticipant participant = new StudyParticipant.Builder().withLanguages(LANGUAGE_SET).build();        
        UserSession session = new UserSession(participant);
        
        LinkedHashSet<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGE_SET, languages);
    }
    
    @Test
    public void canGetLanguagesWhenInHeader() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, "en,fr");

        // This gets called to save the languages retrieved from the header
        ParticipantOptionsService optionsService = mock(ParticipantOptionsService.class);
        controller.setParticipantOptionsService(optionsService);

        CacheProvider cacheProvider = mock(CacheProvider.class);
        controller.setCacheProvider(cacheProvider);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("AAA")
                .withLanguages(Sets.newLinkedHashSet()).build();
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setSessionToken("aSessionToken");
        
        // Verify as well that the values retrieved from the header have been saved in session and ParticipantOptions table.
        LinkedHashSet<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGE_SET, languages);
        
        StudyParticipant updatedParticipant = session.getParticipant();
        assertEquals(LANGUAGE_SET, updatedParticipant.getLanguages());
        
        verify(optionsService).setOrderedStringSet(TEST_STUDY, "AAA", LANGUAGES, LANGUAGE_SET);
        verify(cacheProvider).setUserSession(session);
        
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setCookie(SESSION_TOKEN_HEADER, "aSessionToken", BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
    }
    
    @Test
    public void canGetLanguagesWhenNotInSessionOrHeader() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();

        // This gets called to save the languages retrieved from the header
        ParticipantOptionsService optionsService = mock(ParticipantOptionsService.class);
        controller.setParticipantOptionsService(optionsService);

        CacheProvider cacheProvider = mock(CacheProvider.class);
        controller.setCacheProvider(cacheProvider);
        
        UserSession session = new UserSession();
        
        LinkedHashSet<String> languages = controller.getLanguages(session);
        assertTrue(languages.isEmpty());
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void getSessionAuthenticatedFail() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(false);
    }
    
    @Test
    public void getSessionAuthenticatedSucceed() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session);
        
        UserSession returned = controller.getAuthenticatedSession(false);
        assertEquals(session, returned);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void getSessionAuthenticatedAndConsentedFail() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(true);
    }
    
    @Test
    public void getSessionAuthenticatedAndConsentedSucceed() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session);
        
        UserSession returned = controller.getAuthenticatedSession(true);
        assertEquals(session, returned);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithRoleFail() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(false, Roles.DEVELOPER);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithWrongRoleFail() {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER)).build());
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(false, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithRoleSucceed() {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER)).build());
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session);
        
        UserSession returned = controller.getAuthenticatedSession(false, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    // In this scenario, a user without roles receives the consent required exception
    @Test(expected = ConsentRequiredException.class)
    public void getSessionWithNoRolesConsentedOrRoleFails() {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setAuthenticated(true);
        
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    // In this scenario, a user with roles receives the UnauthorizedException
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithNoConsentConsentedOrRoleFails() {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER)).build());
        session.setAuthenticated(true);
        
        BaseController controller = setupForSessionTest(session);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithConsentedUserNotInRoleSuccess() {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER)).build());
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setAuthenticated(true);
        
        BaseController controller = setupForSessionTest(session);
        
        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    @Test
    public void getSessionWithConsentedUserInRoleSuccess() {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER)).build());
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setAuthenticated(true);
        
        BaseController controller = setupForSessionTest(session);
        
        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    private BaseController setupForSessionTest(UserSession session) {
        BaseController controller = spy(new SchedulePlanController());
        doReturn(session).when(controller).getSessionIfItExists();

        StudyService studyService = mock(StudyService.class);
        controller.setStudyService(studyService);
        
        doNothing().when(controller).verifySupportedVersionOrThrowException(any());
        return controller;
    }
    
    private Map<SubpopulationGuid,ConsentStatus> getConsentStatusMap(boolean consented) {
        return TestUtils.toMap(new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("guid"))
                .withConsented(consented).withSignedMostRecentConsent(consented).build());
    }
    
    private void mockHeader(String header, String value) throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(header)).thenReturn(value);
        mockPlayContext(mockRequest);
    }

}
