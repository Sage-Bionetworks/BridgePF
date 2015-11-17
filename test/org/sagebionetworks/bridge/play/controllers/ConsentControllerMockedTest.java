package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.play.controllers.ConsentController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Http.Context;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

public class ConsentControllerMockedTest {

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
        when(session.getStudyIdentifier()).thenReturn(studyId);
        user = new User();
        user.setHealthCode("healthCode");
        when(session.getUser()).thenReturn(user);

        controller = spy(new ConsentController());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();

        studyService = mock(StudyService.class);
        study = mock(Study.class);
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

        InOrder inOrder = inOrder(optionsService, consentService);
        inOrder.verify(optionsService).setSharingScope(study, "healthCode", SharingScope.NO_SHARING);
        inOrder.verify(consentService).emailConsentAgreement(study, user);
    }

    @Test
    public void consentSignatureJSONCorrect() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-10-10")
                .withImageData("data:asdf").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();

        when(consentService.getConsentSignature(study, user)).thenReturn(sig);

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
    public void consentSignatureHasServerSignedOnValue() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV2();
        
        String response = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(response);
        assertEquals("Consent to research has been recorded.", node.get("message").asText());
        
        validateSignature(captor.getValue());
    }
    
    @Test
    public void consentSignatureHasServerGeneratedSignedOnValue() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        ArgumentCaptor<ConsentSignature> captor = setUpContextWithJson(json);
        
        Result result = controller.giveV2();
        
        String response = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(response);
        assertEquals("Consent to research has been recorded.", node.get("message").asText());
        
        validateSignature(captor.getValue());
    }
    
    @Test
    public void canWithdrawConsent() throws Exception {
        String json = "{\"reason\":\"Because, reasons.\"}";
        Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        
        Result result = controller.withdrawConsent();
        String response = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(response);
        assertEquals("User has been withdrawn from the study.", node.get("message").asText());
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(any(Study.class), any(User.class), captor.capture(), any(Long.class));
        assertEquals("Because, reasons.", captor.getValue().getReason());
        
        verify(cacheProvider).setUserSession(session);
    }

    @Test
    public void canWithdrawConsentWithNoReason() throws Exception {
        String json = "{}";
        Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        ArgumentCaptor<Withdrawal> captor = ArgumentCaptor.forClass(Withdrawal.class);
        
        Result result = controller.withdrawConsent();
        String response = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(response);
        assertEquals("User has been withdrawn from the study.", node.get("message").asText());
        
        verify(consentService).withdrawConsent(any(Study.class), any(User.class), captor.capture(), any(Long.class));
        assertNull(captor.getValue().getReason());
    }
    
    private ArgumentCaptor<ConsentSignature> setUpContextWithJson(String json) throws Exception{
        Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        ArgumentCaptor<ConsentSignature> captor = ArgumentCaptor.forClass(ConsentSignature.class);
        when(consentService.consentToResearch(any(Study.class), any(User.class), captor.capture(),
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
