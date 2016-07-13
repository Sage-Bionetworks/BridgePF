package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Map;

import static org.sagebionetworks.bridge.TestUtils.assertResult;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.controllers.ConsentController;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ConsentControllerMockedTest {

    private static final StudyIdentifierImpl STUDY_IDENTIFIER = new StudyIdentifierImpl("study-key");
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    private ConsentController controller;

    private UserSession session;
    
    private StudyParticipant participant;
    
    @Mock
    private Study study;
    @Mock
    private StudyService studyService;
    @Mock
    private ConsentService consentService;
    @Mock
    private ParticipantOptionsService optionsService;
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private AuthenticationService authenticationService;
    @Captor
    private ArgumentCaptor<ConsentSignature> signatureCaptor;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(UNIX_TIMESTAMP);
        
        participant = new StudyParticipant.Builder().withHealthCode("healthCode").withId("userId").build();
        session = new UserSession(participant); 
        session.setStudyIdentifier(STUDY_IDENTIFIER);
        
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        // legacy controller methods expect there to be a default subpopulation with the id of the study itself (which there always is)
        map.put(SubpopulationGuid.create(STUDY_IDENTIFIER.getIdentifier()), new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID).withName("Default Consent").withRequired(true).build());
        map.put(SUBPOP_GUID, new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID).withName("Another Consent").withRequired(true).build());
        session.setConsentStatuses(map);

        when(study.getIdentifier()).thenReturn(STUDY_IDENTIFIER.getIdentifier());
        when(study.getStudyIdentifier()).thenReturn(STUDY_IDENTIFIER);
        when(studyService.getStudy(session.getStudyIdentifier())).thenReturn(study);
        
        controller = spy(new ConsentController());
        controller.setStudyService(studyService);
        controller.setConsentService(consentService);
        controller.setOptionsService(optionsService);
        controller.setCacheProvider(cacheProvider);
        controller.setAuthenticationService(authenticationService);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        
        doReturn(session).when(authenticationService).updateSession(eq(study), any(CriteriaContext.class));
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

        when(consentService.getConsentSignature(study, SubpopulationGuid.create(study.getIdentifier()), session.getId())).thenReturn(sig);

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
        
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.giveV2();
        assertResult(result, 201, "Consent to research has been recorded.");

        verify(consentService).consentToResearch(eq(study), any(SubpopulationGuid.class), eq(participant),
                signatureCaptor.capture(), any(SharingScope.class), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureHasServerGeneratedSignedOnValueDeprecated() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.giveV2();
        assertResult(result, 201, "Consent to research has been recorded.");
        
        verify(consentService).consentToResearch(eq(study), any(SubpopulationGuid.class), eq(participant),
                signatureCaptor.capture(), any(SharingScope.class), eq(true));
        validateSignature(signatureCaptor.getValue());
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
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create(study.getIdentifier()), participant,
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
        
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create(study.getIdentifier()), participant,
                new Withdrawal(null), 20000);
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void consentSignatureJSONCorrect() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-10-10")
                .withImageData("data:asdf").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();

        when(consentService.getConsentSignature(study, SUBPOP_GUID, session.getId())).thenReturn(sig);

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
        
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        assertResult(result, 201, "Consent to research has been recorded.");
        
        verify(consentService).consentToResearch(eq(study), any(SubpopulationGuid.class), eq(participant),
                signatureCaptor.capture(), any(SharingScope.class), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void consentSignatureHasServerGeneratedSignedOnValue() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        assertResult(result, 201, "Consent to research has been recorded.");
        
        verify(consentService).consentToResearch(eq(study), any(SubpopulationGuid.class), eq(participant),
                signatureCaptor.capture(), any(SharingScope.class), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void canWithdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal("Because, reasons."), 20000);
        
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
        
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal(null), 20000);
    }
    
    @Test
    public void canWithdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"There's a reason for everything.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawFromAllConsents();
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        verify(consentService).withdrawAllConsents(study, session.getId(), new Withdrawal("There's a reason for everything."), 20000);
    }
    
    @Test
    public void emailCopyV2() throws Exception {
        Result result = controller.emailCopyV2(SUBPOP_GUID.getGuid());
        
        assertResult(result, 200, "Emailed consent.");
        
        verify(consentService).emailConsentAgreement(study, SUBPOP_GUID, session.getParticipant());
    }
    
    @Test
    public void consentSignatureHasServerSignedOnValueV2() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        assertResult(result, 201, "Consent to research has been recorded.");
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void canWithdrawConsentV2() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.withdrawConsentV2("test-subpop");
        assertResult(result, 200, "User has been withdrawn from the study.");
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create("test-subpop"), participant,
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
        
        verify(consentService).withdrawConsent(study, SubpopulationGuid.create("test-subpop"), participant,
                new Withdrawal(null), 20000);
        DateTimeUtils.setCurrentMillisSystem();
    }

    private void validateSignature(ConsentSignature signature) {
        assertEquals(UNIX_TIMESTAMP, signature.getSignedOn());
        assertEquals("Jack Aubrey", signature.getName());
        assertEquals("1970-10-10", signature.getBirthdate());
        assertEquals("data:asdf", signature.getImageData());
        assertEquals("image/png", signature.getImageMimeType());
    }

}
