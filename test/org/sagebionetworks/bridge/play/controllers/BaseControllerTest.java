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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.mockito.ArgumentCaptor;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/** Test class for basic utility functions in BaseController. */
@SuppressWarnings("unchecked")
public class BaseControllerTest {
    private static final String DOMAIN = "ws-test.sagebridge.org";
    private static final String HEALTH_CODE = "health-code";
    private static final String IP_ADDRESS = "dummy IP address";
    private static final DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
    private static final ClientInfo CLIENTINFO = ClientInfo.fromUserAgentCache("app/10");
    private static final DateTime UPLOADED_ON = DateTime.now().minusHours(1);
    private static final String USER_ID = "user-id";
    private static final DateTime ACTIVITIES_ACCESSED_ON = DateTime.now().minusHours(2);
    private static final DateTime SIGNED_IN_ON = DateTime.now().minusHours(3);
    private static final String DUMMY_JSON = createJson("{'dummy-key':'dummy-value'}");
    private static final List<String> LANGUAGES = ImmutableList.of("en","fr");
    private static final String TEST_WARNING_MSG = "test warning msg";
    private static final String TEST_WARNING_MSG_2 = "test warning msg 2";
    private static final String TEST_WARNING_MSG_COMBINED = TEST_WARNING_MSG + "; " + TEST_WARNING_MSG_2;
    private static final Map<String, String> TEST_HEADERS;
    static {
        TEST_HEADERS = new HashMap<>();
        TEST_HEADERS.put(BridgeConstants.BRIDGE_API_STATUS_HEADER, TEST_WARNING_MSG);
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void addWarningMsgWorks() throws Exception {
        // mock context
        Http.Response response = TestUtils.mockPlay().withMockResponse().mock();

        BaseController.addWarningMessage(TEST_WARNING_MSG);
        // verify if it set warning header
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
        TestUtils.mockPlay().withHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4").mock();
        
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
        TestUtils.mockPlay().withMockResponse().withHeader(USER_AGENT, 
                "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi").mock();
        
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
        TestUtils.mockPlay().withMockResponse().withHeader(USER_AGENT, 
                "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4").mock();

        Http.Response mockResponse = BaseController.response();
        verify(mockResponse, times(0)).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_USER_AGENT);
    }

    @Test
    public void setWarningHeaderWhenNoUserAgent() throws Exception {
        TestUtils.mockPlay().withMockResponse().mock();

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
        TestUtils.mockPlay().withMockResponse().withHeader(USER_AGENT, "").mock();

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
        TestUtils.mockPlay().withMockResponse().withHeader(USER_AGENT, null).mock();

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
        TestUtils.mockPlay().withHeader(USER_AGENT, 
                "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4").mock();
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 28);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);

    }
    
    @Test
    public void testValidSupportedVersionDoesNotThrowException() throws Exception {
        TestUtils.mockPlay().withHeader(USER_AGENT, 
                "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4").mock();
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testNullSupportedVersionDoesNotThrowException() throws Exception {
        TestUtils.mockPlay().withHeader(USER_AGENT, 
                "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4").mock();
        
        HashMap<String, Integer> map =new HashMap<>();
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testUnknownOSDoesNotThrowException() throws Exception {
        TestUtils.mockPlay().withHeader(USER_AGENT, "Asthma/26 BridgeSDK/4").mock();
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void roleEnforcedWhenRetrievingSession() throws Exception {
        TestUtils.mockPlay().mock();

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
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, 
                "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6").mock();
        
        // with no accept language header at all, things don't break;
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
            
        assertEquals(ImmutableList.of("en", "de"), langs);
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsNull() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, null).withMockResponse().mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);
    }        
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsEmpty() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "").withMockResponse().mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsSimple() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "en-US").mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of("en"), langs);
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsCompoundWithoutWeights() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "FR,en-US").mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of("fr", "en"), langs);
    }
    
    @Test
    public void canRetrieveLanguagesWhenHeaderIsCompoundWithWeights() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "en-US,en;q=0.8").mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of("en"), langs);
    }
    
    // We don't want to throw a BadRequestException due to a malformed header. Just return no languages.
    @Test
    public void badAcceptLanguageHeaderSilentlyIgnored() throws Exception {
        BaseController controller = new SchedulePlanController();
        
        // This is apparently a bad User-Agent header some browser is sending to us; any failure will do though.
        TestUtils.mockPlay().withMockResponse()
            .withHeader(USER_AGENT, "chrome://global/locale/intl.properties").mock();
        
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertTrue(langs.isEmpty());
    }
    
    @Test
    public void canGetLanguagesWhenInSession() {
        // Set up mocks.
        AccountDao mockAccountDao = mock(AccountDao.class);
        SessionUpdateService mockSessionUpdateService = mock(SessionUpdateService.class);

        BaseController controller = new SchedulePlanController();
        controller.setAccountDao(mockAccountDao);
        controller.setSessionUpdateService(mockSessionUpdateService);

        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withLanguages(LANGUAGES).build();
        UserSession session = makeValidSession();
        session.setParticipant(participant);

        // Execute test.
        List<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGES, languages);

        // Participant already has languages. Nothing to save.
        verifyZeroInteractions(mockAccountDao);
        verifyZeroInteractions(mockSessionUpdateService);
    }
    
    @Test
    public void canGetLanguagesWhenInHeader() throws Exception {
        // Set up mocks.
        AccountDao accountDao = mock(AccountDao.class);
        Account account = mock(Account.class);
        TestUtils.mockEditAccount(accountDao, account);

        SessionUpdateService mockSessionUpdateService = mock(SessionUpdateService.class);

        BaseController controller = new SchedulePlanController();
        controller.setAccountDao(accountDao);
        controller.setSessionUpdateService(mockSessionUpdateService);
        
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "en,fr").withMockResponse().mock();

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withLanguages(ImmutableList.of()).build();
        UserSession session = makeValidSession();
        session.setParticipant(participant);
        session.setSessionToken("aSessionToken");
        
        // Verify as well that the values retrieved from the header have been saved in session and ParticipantOptions table.
        List<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGES, languages);

        // Verify we saved the language to the account.
        verify(accountDao).editAccount(eq(TEST_STUDY), eq(HEALTH_CODE), any());
        verify(account).setLanguages(ImmutableList.copyOf(LANGUAGES));

        // Verify we call through to the session update service. (This updates both the cache and the participant, as
        // well as other things outside the scope of this test.)
        ArgumentCaptor<CriteriaContext> contextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        verify(mockSessionUpdateService).updateLanguage(same(session), contextCaptor.capture());
        assertEquals(LANGUAGES, contextCaptor.getValue().getLanguages());
    }
    
    @Test
    public void canGetLanguagesWhenNotInSessionOrHeader() throws Exception {
        // Set up mocks.
        AccountDao accountDao = mock(AccountDao.class);
        SessionUpdateService mockSessionUpdateService = mock(SessionUpdateService.class);
        
        BaseController controller = new SchedulePlanController();
        controller.setAccountDao(accountDao);
        controller.setSessionUpdateService(mockSessionUpdateService);
        TestUtils.mockPlay().withMockResponse().mock();

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withLanguages(Lists.newArrayList()).build();
        UserSession session = makeValidSession();
        session.setParticipant(participant);

        // Execute test.
        List<String> languages = controller.getLanguages(session);
        assertTrue(languages.isEmpty());

        // No languages means nothing to save.
        verifyZeroInteractions(accountDao);
        verifyZeroInteractions(mockSessionUpdateService);
    }

    @Test
    public void doesNotSetWarnHeaderWhenHasAcceptLanguage() throws Exception {
        TestUtils.mockPlay().withMockResponse().withHeader(ACCEPT_LANGUAGE, 
                "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6").mock();

        // verify if it does not set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse, times(0)).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenNoAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withMockResponse().mock();

        // with no accept language header at all, things don't break;
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenEmptyAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "").withMockResponse().mock();

        // with no accept language header at all, things don't break;
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenNullAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, null).withMockResponse().mock();

        // with no accept language header at all, things don't break;
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);

        // verify if it set warning header
        Http.Response mockResponse = BaseController.response();
        verify(mockResponse).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }

    @Test
    public void setWarnHeaderWhenInvalidAcceptLanguage() throws Exception {
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withHeader(ACCEPT_LANGUAGE, "ThisIsAnVvalidAcceptLanguage").withMockResponse().mock();

        // with no accept language header at all, things don't break;
        List<String> langs = controller.getLanguagesFromAcceptLanguageHeader();
        assertEquals(ImmutableList.of(), langs);

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
    public void getSessionPopulatesTheRequestContext() {
        RequestContext context = BridgeUtils.getRequestContext();
        assertNull(context.getId());
        assertNull(context.getCallerStudyId());
        assertEquals(ImmutableSet.of(), context.getCallerSubstudies());
        assertEquals(ImmutableSet.of(), context.getCallerRoles());
        
        Set<String> substudyIds = ImmutableSet.of("substudyA", "substudyB");
        Set<Roles> roles = ImmutableSet.of(Roles.DEVELOPER);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withSubstudyIds(substudyIds)
                .withRoles(roles)
                .build();
        UserSession session = makeValidSession();
        session.setParticipant(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        BaseController controller = setupForSessionTest(session, TestUtils.getValidStudy(BaseControllerTest.class));
        
        controller.getAuthenticatedSession(false);
        
        context = BridgeUtils.getRequestContext();
        assertNotNull(context.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, context.getCallerStudyId());
        assertEquals(substudyIds, context.getCallerSubstudies());
        assertEquals(roles, context.getCallerRoles());
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
        BaseController controller = new SchedulePlanController();
        TestUtils.mockPlay().withJsonBody("{}").withHeader("Bridge-Session", "ABC").withMockResponse().mock();
        
        String token = controller.getSessionToken();
        assertEquals("ABC", token);
    }
    
    @Test
    public void ifClientSendsCookieRetrieveAndResetIt() {
        Http.Cookie mockCookie = mock(Http.Cookie.class);
        doReturn("ABC").when(mockCookie).value();
        
        Http.Request mockRequest = mock(Http.Request.class);
        doReturn(mockCookie).when(mockRequest).cookie(BridgeConstants.SESSION_TOKEN_HEADER);
        
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(mockRequest);

        Http.Response mockResponse = mock(Http.Response.class);
        when(context.response()).thenReturn(mockResponse);
        
        Http.Context.current.set(context);
        
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.get("domain")).thenReturn(DOMAIN);
        when(mockConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        BaseController controller = new SchedulePlanController();
        controller.setBridgeConfig(mockConfig);
        
        String token = controller.getSessionToken();
        assertEquals("ABC", token);
        
        verify(mockResponse).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, "ABC",
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/", DOMAIN, false, false);
    }
    
    @Test
    public void setsCookeForLocal() {
        Http.Cookie mockCookie = mock(Http.Cookie.class);
        doReturn("ABC").when(mockCookie).value();
        
        Http.Request mockRequest = mock(Http.Request.class);
        doReturn(mockCookie).when(mockRequest).cookie(BridgeConstants.SESSION_TOKEN_HEADER);
        
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(mockRequest);

        Http.Response mockResponse = mock(Http.Response.class);
        when(context.response()).thenReturn(mockResponse);
        
        Http.Context.current.set(context);
        
        BaseController controller = new SchedulePlanController();

        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.get("domain")).thenReturn(DOMAIN);
        when(mockConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        controller.setBridgeConfig(mockConfig);
        
        String token = controller.getSessionToken();
        assertEquals("ABC", token);
        
        verify(mockResponse).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, "ABC",
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/", DOMAIN, false, false);
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
                .withLanguages(LANGUAGES)
                .withDataGroups(TestConstants.USER_DATA_GROUPS)
                .withSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
                .withTimeZone(MSK)
                .build();
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY);
        
        BaseController controller = spy(new SchedulePlanController());
        doReturn(CLIENTINFO).when(controller).getClientInfoFromUserAgentHeader();
        controller.setCacheProvider(cacheProvider);
        
        TestUtils.mockPlay().withJsonBody("{}").withHeader("User-Agent", "app/10").mock();
        
        RequestInfo info = controller.getRequestInfoBuilder(session).build();
        
        assertEquals("userId", info.getUserId());
        assertEquals(LANGUAGES, info.getLanguages());
        assertEquals(TestConstants.USER_DATA_GROUPS, info.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, info.getUserSubstudyIds());
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
        doReturn(LANGUAGES).when(controller).getLanguagesFromAcceptLanguageHeader();
        doReturn(CLIENTINFO).when(controller).getClientInfoFromUserAgentHeader();
        doReturn(IP_ADDRESS).when(controller).getRemoteAddress();

        // Execute and validate
        CriteriaContext context = controller.getCriteriaContext(TEST_STUDY);
        assertEquals(TEST_STUDY, context.getStudyIdentifier());
        assertEquals(LANGUAGES, context.getLanguages());
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
                .withDataGroups(TestConstants.USER_DATA_GROUPS).withSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
                .withHealthCode(HEALTH_CODE).withLanguages(LANGUAGES).build();

        UserSession session = new UserSession(participant);
        session.setIpAddress(IP_ADDRESS);
        session.setStudyIdentifier(TEST_STUDY);

        // Execute and validate
        CriteriaContext context = controller.getCriteriaContext(session);
        assertEquals(LANGUAGES, context.getLanguages());
        assertEquals(CLIENTINFO, context.getClientInfo());
        assertEquals(HEALTH_CODE, context.getHealthCode());
        assertEquals(IP_ADDRESS, context.getIpAddress());
        assertEquals(USER_ID, context.getUserId());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, context.getUserSubstudyIds());
        assertEquals(TEST_STUDY, context.getStudyIdentifier());
    }

    @Test
    public void testGetRequestId() throws Exception {
        TestUtils.mockPlay().withHeader(BridgeConstants.X_REQUEST_ID_HEADER, "dummy-request-id").mock();
        BaseController controller = new SchedulePlanController();
        assertEquals("dummy-request-id", controller.getRequestId());
    }

    @Test
    public void getRemoteAddressFromHeader() throws Exception {
        TestUtils.mockPlay().withHeader(BridgeConstants.X_FORWARDED_FOR_HEADER, IP_ADDRESS).mock();
        BaseController controller = new SchedulePlanController();
        assertEquals(IP_ADDRESS, controller.getRemoteAddress());
    }

    @Test
    public void getRemoteAddressFromFallback() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.remoteAddress()).thenReturn(IP_ADDRESS);
        TestUtils.mockPlay().withRequest(mockRequest).mock();

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

}
