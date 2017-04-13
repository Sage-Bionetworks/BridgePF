package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Map;

import static org.sagebionetworks.bridge.TestUtils.assertResult;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
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
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ConsentControllerMockedTest {

    private static final StudyIdentifierImpl STUDY_IDENTIFIER = new StudyIdentifierImpl("study-key");
    
    private static final SubpopulationGuid DEFAULT_SUBPOP_GUID = SubpopulationGuid.create("study-key");
    
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
    @Mock
    private UserSession updatedSession;
    @Captor
    private ArgumentCaptor<ConsentSignature> signatureCaptor;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(UNIX_TIMESTAMP);
        
        participant = new StudyParticipant.Builder().withSharingScope(SharingScope.SPONSORS_AND_PARTNERS)
                .withHealthCode("healthCode").withId("userId").build();
        session = new UserSession(participant); 
        session.setStudyIdentifier(STUDY_IDENTIFIER);
        
        // one default consent and one new consent (neither signed, both required)
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        map.put(DEFAULT_SUBPOP_GUID, new ConsentStatus.Builder().withConsented(false).withGuid(SUBPOP_GUID)
                .withName("Default Consent").withRequired(true).build());
        map.put(SUBPOP_GUID, new ConsentStatus.Builder().withConsented(false).withGuid(SUBPOP_GUID)
                .withName("Another Consent").withRequired(true).build());
        session.setConsentStatuses(map);

        when(study.getIdentifier()).thenReturn(STUDY_IDENTIFIER.getIdentifier());
        when(study.getStudyIdentifier()).thenReturn(STUDY_IDENTIFIER);
        when(studyService.getStudy(session.getStudyIdentifier())).thenReturn(study);
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        // Stubbing the behavior of the consent service to we can validate changes are in the session
        // that is returned to the user.
        sessionUpdateService.setConsentService(consentService);
        
        sessionUpdateService.setCacheProvider(cacheProvider);
        
        controller = spy(new ConsentController());
        controller.setSessionUpdateService(sessionUpdateService);
        controller.setStudyService(studyService);
        controller.setConsentService(consentService);
        controller.setOptionsService(optionsService);
        controller.setCacheProvider(cacheProvider);
        controller.setAuthenticationService(authenticationService);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        
        doReturn(session).when(authenticationService).getSession(eq(study), any(CriteriaContext.class));
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private Answer<Map<SubpopulationGuid,ConsentStatus>> createAnswer(final boolean consenting, final SubpopulationGuid... guids) {
        return new Answer<Map<SubpopulationGuid,ConsentStatus>>() {
            public Map<SubpopulationGuid,ConsentStatus> answer(InvocationOnMock invocation) throws Throwable {
                Map<SubpopulationGuid,ConsentStatus> updatedStatuses = null;
                for (SubpopulationGuid guid : guids) {
                    ConsentStatus oldConsent = session.getConsentStatuses().get(guid);    
                    ConsentStatus updatedConsent = new ConsentStatus.Builder()
                            .withConsentStatus(oldConsent)
                            .withConsented(consenting)
                            .withSignedMostRecentConsent(consenting).build();
                    updatedStatuses = updateMap(session.getConsentStatuses(), guid, updatedConsent);
                }
                return updatedStatuses;
            }
        };
    }
    
    private Map<SubpopulationGuid, ConsentStatus> updateMap(Map<SubpopulationGuid, ConsentStatus> map, SubpopulationGuid guid,
            ConsentStatus status) {
        ImmutableMap.Builder<SubpopulationGuid,ConsentStatus> builder = new ImmutableMap.Builder<SubpopulationGuid,ConsentStatus>();
        for (Map.Entry<SubpopulationGuid,ConsentStatus> entry : map.entrySet()) {
            if (entry.getKey().equals(guid)) {
                builder.put(entry.getKey(), status);
            } else {
                builder.put(entry);
            }
        }
        return builder.build();
    }
    
    @Test
    public void testChangeSharingScope() throws Exception {
        Result result = controller.changeSharingScope(SharingScope.NO_SHARING, "message");
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("no_sharing", node.get("sharingScope").asText());
        assertFalse(node.get("dataSharing").asBoolean());

        verify(optionsService).setEnum(study.getStudyIdentifier(), "healthCode", SHARING_SCOPE, SharingScope.NO_SHARING);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureJSONCorrectDeprecated() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-10-10")
                .withImageData("data:asdf").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();

        when(consentService.getConsentSignature(study, DEFAULT_SUBPOP_GUID, session.getId())).thenReturn(sig);

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
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, DEFAULT_SUBPOP_GUID));

        // This signs the default consent
        Result result = controller.giveV2();
        
        assertConsentInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);

        verify(consentService).consentToResearch(eq(study), eq(DEFAULT_SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void consentSignatureHasServerGeneratedSignedOnValueDeprecated() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":0,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, DEFAULT_SUBPOP_GUID));
        
        Result result = controller.giveV2();
        
        assertConsentInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);
        
        verify(consentService).consentToResearch(eq(study), eq(DEFAULT_SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void canWithdrawConsentDeprecated() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, SUBPOP_GUID));

        Result result = controller.withdrawConsent();
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, DEFAULT_SUBPOP_GUID, participant,
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
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, DEFAULT_SUBPOP_GUID));

        Result result = controller.withdrawConsent();
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);
        
        verify(consentService).withdrawConsent(study, DEFAULT_SUBPOP_GUID, participant, new Withdrawal(null), 20000);
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
    
    @Test(expected = EntityNotFoundException.class)
    public void givingConsentToInvalidSubpopulation() throws Exception {
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        controller.giveV3("bad-guid");
    }
    
    @Test
    public void consentUpdatesSession() throws Exception {
        doReturn(updatedSession).when(authenticationService).getSession(eq(study), any(CriteriaContext.class));
        
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID));
        
        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        assertConsentInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        verify(cacheProvider).setUserSession(any());
    }
    
    @Test
    public void consentSignatureHasServerSignedOnValue() throws Exception {
        // signedOn will be set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"sponsors_and_partners\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID));

        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        assertConsentInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.SPONSORS_AND_PARTNERS), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void consentSignatureHasServerGeneratedSignedOnValue() throws Exception {
        // This signedOn property should be ignored, it is always set on the server
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":1000,\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID));

        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        assertConsentInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void canWithdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        // Session that is returned no longer consents
        doReturn(false).when(updatedSession).doesConsent();
        doReturn(participant).when(updatedSession).getParticipant();
        doReturn(updatedSession).when(authenticationService).getSession(eq(study), any(CriteriaContext.class));
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, SUBPOP_GUID));

        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        // Given the mock session, the user is withdrawn from everything, so sharing is set to NO_SHARING
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal("Because, reasons."),
                20000);
        verify(cacheProvider).setUserSession(any());
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void canWithdrawConsentWithNoReason() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, SUBPOP_GUID));
        
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal(null), 20000);
    }
    
    @Test
    public void canWithdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"There's a reason for everything.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID, DEFAULT_SUBPOP_GUID));
        
        Result result = controller.withdrawFromAllConsents();
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
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
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"imageData\":\"data:asdf\",\"imageMimeType\":\"image/png\",\"scope\":\"all_qualified_researchers\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID));

        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        // Sharing is still off because there's another required consent that hasn't been signed. 
        // Practically, only the last sharing option that is set will be used. This did not used to 
        // be true, and was an inconsistency in the model when applied to multiple consents.
        assertConsentInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.ALL_QUALIFIED_RESEARCHERS), eq(true));
        validateSignature(signatureCaptor.getValue());
    }
    
    @Test
    public void canWithdrawConsentV2() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);

        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, DEFAULT_SUBPOP_GUID));

        Result result = controller.withdrawConsentV2(DEFAULT_SUBPOP_GUID.getGuid());
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);
        
        // Should call the service and withdraw
        verify(consentService).withdrawConsent(study, DEFAULT_SUBPOP_GUID, participant,
                new Withdrawal("Because, reasons."), 20000);
        
        verify(cacheProvider).setUserSession(any());
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void canWithdrawConsentWithNoReasonV2() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        String json = "{}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, SUBPOP_GUID));
        
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        verify(consentService).withdrawConsent(study, SUBPOP_GUID, participant,
                new Withdrawal(null), 20000);
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    // Current behavior is to document signedOn as a DateTime field, though all these tests assume a 
    // long is sent. Not only does this lead to conversion errors, but we set the signedOn value ourselves 
    // on the server. This test verifies that even if you send a date & time string, you do not receive an 
    // error, and it is still ignored. 
    @Test
    public void submittingDateTimeStringsDoesNotCauseException() throws Exception {
        String json = "{\"name\":\"Jack Aubrey\",\"birthdate\":\"1970-10-10\",\"signedOn\":\"2016-12-19T17:40:33.812Z\",\"scope\":\"no_sharing\"}";
        
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(true, SUBPOP_GUID));

        Result result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        assertConsentInSession(result, SharingScope.NO_SHARING, SUBPOP_GUID);
        
        JsonNode node = TestUtils.getJson(result);
        assertEquals("userId", node.get("id").asText());
        
        verify(consentService).consentToResearch(eq(study), eq(SUBPOP_GUID), eq(participant),
                signatureCaptor.capture(), eq(SharingScope.NO_SHARING), eq(true));
        
        ConsentSignature sig = signatureCaptor.getValue();
        assertNotEquals(DateTime.parse("2016-12-19T17:40:33.812Z").getMillis(), sig.getSignedOn());
        assertEquals("Jack Aubrey", sig.getName());
        assertEquals("1970-10-10", sig.getBirthdate());
    }
    
    @Test
    public void sharingChangedIfRequiredConsentRemains() throws Exception {
        // Change the session so user is consented to two different consents
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        map.put(DEFAULT_SUBPOP_GUID, new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID)
                        .withSignedMostRecentConsent(true).withName("Default Consent").withRequired(true).build());
        map.put(SUBPOP_GUID, new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID)
                .withSignedMostRecentConsent(true).withName("Another Consent").withRequired(true).build());
        session.setConsentStatuses(map);
        
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, session.getParticipant().getSharingScope());
        
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, DEFAULT_SUBPOP_GUID));
        
        // withdrawing one consent will turn off sharing because not all required consents are signed 
        Result result = controller.withdrawConsentV2(DEFAULT_SUBPOP_GUID.getGuid());
        
        assertWithdrawnInSession(result, SharingScope.NO_SHARING, DEFAULT_SUBPOP_GUID);
    }

    @Test
    public void sharingNotChangedIfOptionalConsentRemains() throws Exception {
        // Change the session so user is consented to two different consents
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        map.put(DEFAULT_SUBPOP_GUID, new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID)
                        .withSignedMostRecentConsent(true).withName("Default Consent").withRequired(true).build());
        map.put(SUBPOP_GUID, new ConsentStatus.Builder().withConsented(true).withGuid(SUBPOP_GUID)
                .withSignedMostRecentConsent(true).withName("Another Consent").withRequired(false).build());
        session.setConsentStatuses(map);
        
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, session.getParticipant().getSharingScope());
        
        String json = "{\"reason\":\"Because, reasons.\"}";
        TestUtils.mockPlayContextWithJson(json);
        
        when(consentService.getConsentStatuses(any())).thenAnswer(createAnswer(false, SUBPOP_GUID));

        // withdrawing the optional consent does not turn off sharing 
        Result result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        assertWithdrawnInSession(result, SharingScope.SPONSORS_AND_PARTNERS, SUBPOP_GUID);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingSuspendedUpdatesSession() throws Exception {
        Result result = controller.suspendDataSharing();

        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("no_sharing", node.get("sharingScope").asText());
        assertFalse(node.get("dataSharing").asBoolean());

        verify(optionsService).setEnum(study.getStudyIdentifier(), "healthCode", SHARING_SCOPE, SharingScope.NO_SHARING);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingResumedUpdatesSession() throws Exception {
        Result result = controller.resumeDataSharing();

        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("sponsors_and_partners", node.get("sharingScope").asText());
        assertTrue(node.get("dataSharing").asBoolean());

        verify(optionsService).setEnum(study.getStudyIdentifier(), "healthCode", SHARING_SCOPE, SharingScope.SPONSORS_AND_PARTNERS);
    }
    
    private void assertConsentInSession(Result result, SharingScope scope, SubpopulationGuid subpopGuid) throws Exception {
        assertEquals(201, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("userId", node.get("id").asText());
        assertEquals(scope.name().toLowerCase(), node.get("sharingScope").asText());
        
        JsonNode consent = node.get("consentStatuses").get(subpopGuid.getGuid());
        assertTrue(consent.get("consented").asBoolean());
        assertTrue(consent.get("signedMostRecentConsent").asBoolean());
    }
    
    private void assertWithdrawnInSession(Result result, SharingScope sharingScope, SubpopulationGuid subpopGuid) throws Exception {
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);

        assertEquals("userId", node.get("id").asText());
        assertEquals(sharingScope.name().toLowerCase(), node.get("sharingScope").asText());
        assertEquals(sharingScope != SharingScope.NO_SHARING, node.get("dataSharing").asBoolean());
        
        JsonNode consent = node.get("consentStatuses").get(subpopGuid.getGuid());
        assertFalse(consent.get("consented").asBoolean());
        assertFalse(consent.get("signedMostRecentConsent").asBoolean());
    }
    
    private void validateSignature(ConsentSignature signature) {
        assertEquals(UNIX_TIMESTAMP, signature.getSignedOn());
        assertEquals("Jack Aubrey", signature.getName());
        assertEquals("1970-10-10", signature.getBirthdate());
        assertEquals("data:asdf", signature.getImageData());
        assertEquals("image/png", signature.getImageMimeType());
    }

}
