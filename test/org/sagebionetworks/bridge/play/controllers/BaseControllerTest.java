package org.sagebionetworks.bridge.play.controllers;

import static org.apache.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.sagebionetworks.bridge.BridgeConstants.*;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.newLinkedHashSet;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import play.mvc.Http;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/** Test class for basic utility functions in BaseController. */
@SuppressWarnings("unchecked")
public class BaseControllerTest {
    private static final String DOMAIN = "ws-test.sagebridge.org";
    private static final String HEALTH_CODE = "health-code";
    private static final String IP_ADDRESS = "dummy IP address";
    private static final DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
    private static final Set<String> GROUPS = Sets.newHashSet("group1");
    private static final ClientInfo CLIENTINFO = ClientInfo.fromUserAgentCache("app/10");
    private static final DateTime UPLOADED_ON = DateTime.now().minusHours(1);
    private static final String USER_ID = "user-id";
    private static final DateTime ACTIVITIES_ACCESSED_ON = DateTime.now().minusHours(2);
    private static final DateTime SIGNED_IN_ON = DateTime.now().minusHours(3);
    private static final String DUMMY_JSON = createJson("{'dummy-key':'dummy-value'}");
    private static final LinkedHashSet<String> LANGUAGE_SET = newLinkedHashSet("en","fr");
    private static final String TEST_WARNING_MSG = "test warning msg";
    private static final String TEST_WARNING_MSG_2 = "test warning msg 2";
    private static final String TEST_WARNING_MSG_COMBINED = TEST_WARNING_MSG + "; " + TEST_WARNING_MSG_2;
    private static final Map<String, String> TEST_HEADERS;
    static {
        TEST_HEADERS = new HashMap<>();
        TEST_HEADERS.put(BridgeConstants.BRIDGE_API_STATUS_HEADER, TEST_WARNING_MSG);
    }

    @Test
    public void addWarningMsgWorks() throws Exception {
        // mock context
        Http.Context context = mock(Http.Context.class);
        Http.Response mockResponse = mock(Http.Response.class);
        when(context.response()).thenReturn(mockResponse);
        Http.Context.current.set(context);

        BaseController.addWarningMessage(TEST_WARNING_MSG);
        // verify if it set warning header
        Http.Response response = Http.Context.current().response();
        verify(response).setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, TEST_WARNING_MSG);

        // verify if it append new warning msg
        when(response.getHeaders()).thenReturn(TEST_HEADERS);
        BaseController.addWarningMessage(TEST_WARNING_MSG_2);
        verify(response).setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, TEST_WARNING_MSG_COMBINED);
    }

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
    public void doesNotSetWarningHeaderWhenHasUserAgent() throws Exception {
        mockPlayContext();
        mockHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse, times(0)).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);
    }

    @Test
    public void setWarningHeaderWhenNoUserAgent() throws Exception {
        mockPlayContext();

        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);

        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }

    @Test
    public void setWarningHeaderWhenEmptyUserAgent() throws Exception {
        mockPlayContext();
        mockHeader(USER_AGENT, "");

        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);

        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }

    @Test
    public void setWarningHeaderWhenNullUserAgent() throws Exception {
        mockPlayContext();
        mockHeader(USER_AGENT, null);

        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);

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

        // Mock participant and session.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        doReturn(session).when(controller).getSessionIfItExists();

        // Mock study.
        Study study = TestUtils.getValidStudy(BaseControllerTest.class);
        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        controller.setStudyService(mockStudyService);

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
        AccountDao accountDao = mock(AccountDao.class);
        Account account = mock(Account.class);
        TestUtils.mockEditAccount(accountDao, account);
        
        BaseController controller = new SchedulePlanController();
        controller.setAccountDao(accountDao);
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, "en,fr");

        CacheProvider cacheProvider = mock(CacheProvider.class);
        controller.setCacheProvider(cacheProvider);
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(cacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);
        
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
        
        verify(account).setLanguages(LANGUAGE_SET);
        verify(cacheProvider).setUserSession(session);
    }
    
    @Test
    public void canGetLanguagesWhenNotInSessionOrHeader() throws Exception {
        AccountDao accountDao = mock(AccountDao.class);
        
        BaseController controller = new SchedulePlanController();
        controller.setAccountDao(accountDao);
        mockPlayContext();

        CacheProvider cacheProvider = mock(CacheProvider.class);
        controller.setCacheProvider(cacheProvider);
        
        UserSession session = new UserSession();
        
        LinkedHashSet<String> languages = controller.getLanguages(session);
        assertTrue(languages.isEmpty());
        
        verify(accountDao, never()).editAccount(any(), any(), any());
    }

    @Test
    public void doesNotSetWarnHeaderWhenHasAcceptLanguage() throws Exception {
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");

        // verify if it does not set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse, times(0)).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenNoAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();

        // with no accept language header at all, things don't break;
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        // testing this because the rest of these tests will use ImmutableSet.of()
        assertTrue(langs instanceof LinkedHashSet);
        assertEquals(ImmutableSet.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenEmptyAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, "");

        // with no accept language header at all, things don't break;
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        // testing this because the rest of these tests will use ImmutableSet.of()
        assertTrue(langs instanceof LinkedHashSet);
        assertEquals(ImmutableSet.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenNullAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, null);

        // with no accept language header at all, things don't break;
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        // testing this because the rest of these tests will use ImmutableSet.of()
        assertTrue(langs instanceof LinkedHashSet);
        assertEquals(ImmutableSet.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenInvalidAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        mockPlayContext();
        mockHeader(ACCEPT_LANGUAGE, "ThisIsAnVvalidAcceptLanguage");

        // with no accept language header at all, things don't break;
        LinkedHashSet<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        // testing this because the rest of these tests will use ImmutableSet.of()
        assertTrue(langs instanceof LinkedHashSet);
        assertEquals(ImmutableSet.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getSessionAuthenticatedFail() {
        BaseController controller = setupForSessionTest(makeValidSession(),
                TestUtils.getValidStudy(BaseControllerTest.class));
        controller.getAuthenticatedSession(false);
    }
    
    @Test
    public void getSessionAuthenticatedSucceed() {
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        UserSession returned = controller.getAuthenticatedSession(false);
        assertEquals(session, returned);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void ipLockingForPrivilegedAccounts() {
        // Setup test
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();

        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setParticipant(participant);

        Study study = TestUtils.getValidStudy(BaseControllerTest.class);
        study.setParticipantIpLockingEnabled(false);

        BaseController controller = setupForSessionTest(session, study);
        doReturn("different address").when(controller).getRemoteAddress();

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void ipLockingForParticipantsEnabled() {
        // Setup test
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setIpAddress("original address");

        Study study = TestUtils.getValidStudy(BaseControllerTest.class);
        study.setParticipantIpLockingEnabled(true);

        BaseController controller = setupForSessionTest(session, study);
        doReturn("different address").when(controller).getRemoteAddress();

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingForParticipantsDisabled() {
        // Setup test
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setIpAddress("original address");

        Study study = TestUtils.getValidStudy(BaseControllerTest.class);
        study.setParticipantIpLockingEnabled(false);

        BaseController controller = setupForSessionTest(session, study);
        doReturn("different address").when(controller).getRemoteAddress();

        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingSameIpAddress() {
        // Setup test - Append different load balancers to make sure we handle this properly.
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();

        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setIpAddress("same address, load balancer A");
        session.setParticipant(participant);

        Study study = TestUtils.getValidStudy(BaseControllerTest.class);
        study.setParticipantIpLockingEnabled(false);

        BaseController controller = setupForSessionTest(session, study);
        doReturn("same address, load balancer B").when(controller).getRemoteAddress();

        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getSessionAuthenticatedAndConsentedFail() {
        BaseController controller = setupForSessionTest(makeValidSession(),
                TestUtils.getValidStudy(BaseControllerTest.class));
        controller.getAuthenticatedSession(true);
    }
    
    @Test
    public void getSessionAuthenticatedAndConsentedSucceed() {
        UserSession session = makeValidSession();
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        UserSession returned = controller.getAuthenticatedSession(true);
        assertEquals(session, returned);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithRoleFail() {
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        controller.getAuthenticatedSession(false, Roles.DEVELOPER);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithWrongRoleFail() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER))
                .build();
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        controller.getAuthenticatedSession(false, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithRoleSucceed() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER))
                .build();
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        UserSession returned = controller.getAuthenticatedSession(false, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    // In this scenario, a user without roles receives the consent required exception
    @Test(expected = ConsentRequiredException.class)
    public void getSessionWithNoRolesConsentedOrRoleFails() {
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    // In this scenario, a user with roles receives the UnauthorizedException
    @Test(expected = UnauthorizedException.class)
    public void getSessionWithNoConsentConsentedOrRoleFails() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER))
                .build();
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithConsentedUserNotInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER))
                .build();
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setParticipant(participant);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));

        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    @Test
    public void getSessionWithConsentedUserInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER))
                .build();
        UserSession session = makeValidSession();
        session.setAuthenticated(true);
        session.setConsentStatuses(getConsentStatusMap(true));
        session.setParticipant(participant);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    @Test
    public void ifClientSendsHeaderRetrieveIt() throws Exception {
        Map<String,String[]> headers = Maps.newHashMap();
        headers.put("Bridge-Session", new String[] {"ABC"});
        
        TestUtils.mockPlayContextWithJson("{}", headers);
        BaseController controller = new SchedulePlanController();
        
        String token = controller.getSessionToken();
        assertEquals("ABC", token);
    }
    
    @Test
    public void getRequestInfoBuilder() throws Exception {
        // Set the dates and verify they are retrieved from cache and added to response
        RequestInfo persistedInfo = new RequestInfo.Builder()
                .withSignedInOn(SIGNED_IN_ON)
                .withActivitiesAccessedOn(ACTIVITIES_ACCESSED_ON)
                .withUploadedOn(UPLOADED_ON).build();
        
        CacheProvider cacheProvider = mock(CacheProvider.class);
        doReturn(persistedInfo).when(cacheProvider).getRequestInfo("userId");
        
        UserSession session = new UserSession();
        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("userId")
                .withLanguages(LANGUAGE_SET)
                .withDataGroups(GROUPS)
                .withTimeZone(MSK)
                .build();
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY);
        
        BaseController controller = spy(new SchedulePlanController());
        doReturn(CLIENTINFO).when(controller).getClientInfoFromUserAgentHeader();
        controller.setCacheProvider(cacheProvider);
        
        Map<String,String[]> headers = Maps.newHashMap();
        headers.put("User-Agent", new String[]{"app/10"});
        TestUtils.mockPlayContextWithJson("{}", headers);
        
        RequestInfo info = controller.getRequestInfoBuilder(session).build();
        
        assertEquals("userId", info.getUserId());
        assertEquals(LANGUAGE_SET, info.getLanguages());
        assertEquals(GROUPS, info.getUserDataGroups());
        assertEquals(MSK, info.getTimeZone());
        assertEquals("app/10", info.getUserAgent());
        assertEquals(CLIENTINFO, info.getClientInfo());
        assertEquals(TEST_STUDY, info.getStudyIdentifier());
        assertEquals(ACTIVITIES_ACCESSED_ON.withZone(MSK), info.getActivitiesAccessedOn());
        assertEquals(UPLOADED_ON.withZone(MSK), info.getUploadedOn());
        assertEquals(SIGNED_IN_ON.withZone(MSK), info.getSignedInOn());
    }

    @Test
    public void getCriteriaContextForStudy() {
        // Set up BaseController, spy out methods that are tested elsewhere.
        BaseController controller = spy(new SchedulePlanController());
        doReturn(LANGUAGE_SET).when(controller).getLanguagesFromAcceptLanguageHeader();
        doReturn(CLIENTINFO).when(controller).getClientInfoFromUserAgentHeader();
        doReturn(IP_ADDRESS).when(controller).getRemoteAddress();

        // Execute and validate
        CriteriaContext context = controller.getCriteriaContext(TEST_STUDY);
        assertEquals(TEST_STUDY, context.getStudyIdentifier());
        assertEquals(LANGUAGE_SET, context.getLanguages());
        assertEquals(CLIENTINFO, context.getClientInfo());
        assertEquals(IP_ADDRESS, context.getIpAddress());
    }

    @Test
    public void getCriteriaContextForSession() {
        // Set up BaseController, spy out methods that are tested elsewhere.
        BaseController controller = spy(new SchedulePlanController());
        doReturn(CLIENTINFO).when(controller).getClientInfoFromUserAgentHeader();

        // Set up participant and session.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withDataGroups(TestConstants.USER_DATA_GROUPS).withHealthCode(HEALTH_CODE).withLanguages(LANGUAGE_SET)
                .build();

        UserSession session = new UserSession(participant);
        session.setIpAddress(IP_ADDRESS);
        session.setStudyIdentifier(TEST_STUDY);

        // Execute and validate
        CriteriaContext context = controller.getCriteriaContext(session);
        assertEquals(LANGUAGE_SET, context.getLanguages());
        assertEquals(CLIENTINFO, context.getClientInfo());
        assertEquals(HEALTH_CODE, context.getHealthCode());
        assertEquals(IP_ADDRESS, context.getIpAddress());
        assertEquals(USER_ID, context.getUserId());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getUserDataGroups());
        assertEquals(TEST_STUDY, context.getStudyIdentifier());
    }

    @Test
    public void testGetRequestId() throws Exception {
        mockHeader(BridgeConstants.X_REQUEST_ID_HEADER, "dummy-request-id");
        BaseController controller = new SchedulePlanController();
        assertEquals("dummy-request-id", controller.getRequestId());
    }

    @Test
    public void getRemoteAddressFromHeader() throws Exception {
        mockHeader(BridgeConstants.X_FORWARDED_FOR_HEADER, IP_ADDRESS);
        BaseController controller = new SchedulePlanController();
        assertEquals(IP_ADDRESS, controller.getRemoteAddress());
    }

    @Test
    public void getRemoteAddressFromFallback() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.remoteAddress()).thenReturn(IP_ADDRESS);
        mockPlayContext(mockRequest);

        BaseController controller = new SchedulePlanController();
        assertEquals(IP_ADDRESS, controller.getRemoteAddress());
    }

    private BaseController setupForSessionTest(UserSession session, Study study) {
        BaseController controller = spy(new SchedulePlanController());
        doReturn(session).when(controller).getSessionIfItExists();
        doReturn(null).when(controller).getRemoteAddress();

        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy(TEST_STUDY)).thenReturn(study);
        controller.setStudyService(studyService);
        
        doNothing().when(controller).verifySupportedVersionOrThrowException(any());
        return controller;
    }

    private static UserSession makeValidSession() {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        return session;
    }

    private Map<SubpopulationGuid,ConsentStatus> getConsentStatusMap(boolean consented) {
        return TestUtils.toMap(new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("guid"))
                .withConsented(consented).withSignedMostRecentConsent(consented).build());
    }
    
    private void mockHeader(String header, String value) throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(header)).thenReturn(value);
        when(mockRequest.headers()).thenReturn(ImmutableMap.of(header, new String[] { value }));
        mockPlayContext(mockRequest);
    }

}
