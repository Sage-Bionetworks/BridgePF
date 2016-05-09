package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;
import static org.sagebionetworks.bridge.TestUtils.assertResult;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.controllers.ConsentController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;
import play.test.Helpers;

public class ConsentControllerMockedTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();

    private UserSession session;
    private User user;
    private Study study;
    private ConsentController controller;

    private StudyService studyService;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private CacheProvider cacheProvider;

    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(UNIX_TIMESTAMP);
        
        session = mock(UserSession.class);
        StudyIdentifier studyId = mock(StudyIdentifier.class);
        when(studyId.getIdentifier()).thenReturn("study-key");
        when(session.getStudyIdentifier()).thenReturn(studyId);
        user = new User();
        user.setHealthCode("healthCode");
        when(session.getUser()).thenReturn(user);

        controller = spy(new ConsentController());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();

        studyService = mock(StudyService.class);
        study = mock(Study.class);
        when(study.getIdentifier()).thenReturn("study-key");
        when(study.getStudyIdentifier()).thenReturn(studyId);
        when(studyService.getStudy(studyId)).thenReturn(study);
        controller.setStudyService(studyService);

        consentService = mock(ConsentService.class);
        controller.setConsentService(consentService);

        optionsService = mock(ParticipantOptionsService.class);
        controller.setOptionsService(optionsService);

        cacheProvider = mock(CacheProvider.class);
        controller.setCacheProvider(cacheProvider);
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void testChangeSharingScope() {
        controller.changeSharingScope(SharingScope.NO_SHARING, "message");

        verify(optionsService).setEnum(study, "healthCode", SHARING_SCOPE, SharingScope.NO_SHARING);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureJSONCorrectDeprecated() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-10-10")
                .withImageData("data:asdf").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();

        when(consentService.getConsentSignature(study, SubpopulationGuid.create(study.getIdentifier()), session)).thenReturn(sig);

        Result result = controller.getConsentSignature();

        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(5, TestUtils.getFieldNamesSet(node).size());
        assertEquals("Jack Aubrey", node.get("name").asText());
        assertEquals("1970-10-10", node.get("birthdate").asText());
        assertEquals("ConsentSignature", node.get("type").asText());
        assertEquals("data:asdf", node.get("imageData").asText());
        assertEquals("image/png", node.get("imageMimeType").asText());
        // no signedOn value when serializing
    }

    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureHasServerSignedOnValueDeprecated() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV2();
        assertResult(result, 201, "Consent to research has been recorded.");

        validateSignature(captor.getValue());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureHasServerGeneratedSignedOnValueDeprecated() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV2();
        assertResult(result, 201, "Consent to research has been recorded.");
        
        validateSignature(captor.getValue());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void canWithdrawConsentDeprecated() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsent();
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create(study.getIdentifier()), session,
                new Withdrawal("Because, reasons."), 20000);
        
        verify(cacheProvider).setUserSession(session);
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void canWithdrawConsentWithNoReasonDeprecated() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsent();
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create(study.getIdentifier()), session,
                new Withdrawal(null), 20000);
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void consentSignatureJSONCorrect() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-10-10")
                .withImageData("data:asdf").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();

        when(consentService.getConsentSignature(study, SUBPOP_GUID, session)).thenReturn(sig);

        Result result = controller.getConsentSignatureV2(SUBPOP_GUID.getGuid());

        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(5, TestUtils.getFieldNamesSet(node).size());
        assertEquals("Jack Aubrey", node.get("name").asText());
        assertEquals("1970-10-10", node.get("birthdate").asText());
        assertEquals("ConsentSignature", node.get("type").asText());
        assertEquals("data:asdf", node.get("imageData").asText());
        assertEquals("image/png", node.get("imageMimeType").asText());
        // no signedOn value when serializing
    }

    @Test
    public void consentSignatureHasServerSignedOnValue() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        assertResult(result, 201, "Consent to research has been recorded.");
        
        validateSignature(captor.getValue());
    }
    
    @Test
    public void consentSignatureHasServerGeneratedSignedOnValue() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        assertResult(result, 201, "Consent to research has been recorded.");
        
        validateSignature(captor.getValue());
    }
    
    @Test
    public void canWithdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, session, new Withdrawal("Because, reasons."), 20000);
        
        verify(cacheProvider).setUserSession(session);
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void canWithdrawConsentWithNoReason() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, session, new Withdrawal(null), 20000);
    }
    
    @Test
    public void emailCopyV2() throws Exception {
        Result result = controller.emailCopyV2(SUBPOP_GUID.getGuid());
        
        assertResult(result, 200, "Emailed consent.");
        
        verify(consentService).emailConsentAgreement(study, SUBPOP_GUID, session);
    }
    
    @Test
    public void consentSignatureHasServerSignedOnValueV2() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV3("test-subpop");
        assertResult(result, 201, "Consent to research has been recorded.");
        
        verify(consentService).consentToResearch(eq(study), eq(SubpopulationGuid.create("test-subpop")), eq(session),
                captor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        
        validateSignature(captor.getValue());
    }
    
    @Test
    public void canWithdrawConsentV2() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2("test-subpop");
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create("test-subpop"), session,
                new Withdrawal("Because, reasons."), 20000);
        
        verify(cacheProvider).setUserSession(session);
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void canWithdrawConsentWithNoReasonV2() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2("test-subpop");
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create("test-subpop"), session,
                new Withdrawal(null), 20000);
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private ArgumentCaptor<ConsentSignature> setUpContextWithJson(String json) throws Exception{
        TestUtils.mockPlayContextWithJson(json);
        
        ArgumentCaptor<ConsentSignature> captor = ArgumentCaptor.forClass(ConsentSignature.class);
        when(consentService.consentToResearch(any(Study.class), any(SubpopulationGuid.class), any(UserSession.class), captor.capture(),
                any(SharingScope.class), any(Boolean.class))).thenReturn(user);
        return captor;
    }
    
    private void validateSignature(ConsentSignature signature) {
        assertEquals(UNIX_TIMESTAMP, signature.getSignedOn());
        assertEquals("Jack Aubrey", signature.getName());
        assertEquals("1970-10-10", signature.getBirthdate());
        assertEquals("data:asdf", signature.getImageData());
        assertEquals("image/png", signature.getImageMimeType());
    }

}
