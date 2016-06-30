package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final TypeReference<PagedResourceList<ScheduledActivity>> PAGED_ACTIVITIES_REF = new TypeReference<PagedResourceList<ScheduledActivity>>() {};
    
    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(Roles.RESEARCHER);
    
    private static final String ID = "ASDF";

    private static final String EMAIL = "email@email.com";

    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};

    private static final AccountSummary SUMMARY = new AccountSummary("firstName", "lastName", "email", "id",
            DateTime.now(), AccountStatus.ENABLED, TestConstants.TEST_STUDY);
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private AuthenticationService authService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Captor
    private ArgumentCaptor<Map<ParticipantOption,String>> optionMapCaptor;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    
    private UserSession session;
    
    private Study study;
    
    @Before
    public void before() throws Exception {
        study = new DynamoStudy();
        study.setUserProfileAttributes(Sets.newHashSet("foo","baz"));
        study.setIdentifier("test-study");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(CALLER_ROLES)
                .withId(ID).build();
        
        session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);

        doReturn(session).when(controller).getSessionIfItExists();
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, 10, 20, 30).withFilter("emailFilter", "foo");
        
        when(authService.updateSession(eq(study), any(), eq(ID))).thenReturn(session);
        
        when(participantService.getPagedAccountSummaries(eq(study), anyInt(), anyInt(), any())).thenReturn(page);
        
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authService);
        controller.setCacheProvider(cacheProvider);
        
        mockPlayContext();
    }
    
    @Test
    public void getParticipants() throws Exception {
        Result result = controller.getParticipants("10", "20", "foo");
        PagedResourceList<AccountSummary> page = resultToPage(result);
        
        // verify the result contains items
        assertEquals(3, page.getItems().size());
        assertEquals(30, page.getTotal());
        assertEquals(SUMMARY, page.getItems().get(0));
        
        //verify paging/filtering
        assertEquals(new Integer(10), page.getOffsetBy());
        assertEquals(20, page.getPageSize());
        assertEquals("foo", page.getFilters().get("emailFilter"));
        verify(participantService).getPagedAccountSummaries(study, 10, 20, "foo");
    }
    
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null);
        
        // paging with defaults
        verify(participantService).getPagedAccountSummaries(study, 0, API_DEFAULT_PAGE_SIZE, null);
    }

    @Test
    public void getParticipant() throws Exception {
        study.setHealthCodeExportEnabled(true);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE).build();
        
        when(participantService.getParticipant(study, ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        assertEquals(result.contentType(), "application/json");
        String json = Helpers.contentAsString(result);
        
        // StudyParticipant will encrypt the healthCode when you ask for it, so validate the
        // JSON itself.
        JsonNode node = MAPPER.readTree(json);
        assertTrue(node.has("firstName"));
        assertTrue(node.has("healthCode"));
        assertFalse(node.has("encryptedHealthCode"));
    }
    
    @Test
    public void getParticipantWithNoHealthCode() throws Exception {
        study.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").withHealthCode("healthCode").build();
        when(participantService.getParticipant(study, ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        String json = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);
        
        assertEquals("Test", retrievedParticipant.getFirstName());
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test
    public void signUserOut() throws Exception {
        controller.signOut(ID);
        
        verify(participantService).signUserOut(study, ID);
    }

    @Test
    public void updateParticipant() throws Exception {
        study.getUserProfileAttributes().add("phone");
        mockPlayContextWithJson(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},"+
                "'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant(ID);
        assertResult(result, 200, "Participant updated.");
        
        verify(participantService).updateParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(ID, participant.getId());
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(study, 0, API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder("ABCD");
        doReturn(holder).when(participantService).createParticipant(eq(study), any(), any(StudyParticipant.class), eq(true));
        
        study.getUserProfileAttributes().add("phone");
        mockPlayContextWithJson(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},"+
                "'languages':['en','fr']}"));
        
        Result result = controller.createParticipant();
        
        assertEquals(201, result.status());
        String id = MAPPER.readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(participantService).createParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture(), eq(true));
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }

    @Test
    public void updateParticipantWithMismatchedIdsUsesURL() throws Exception {
        mockPlayContextWithJson(createJson("{'id':'id2'}"));
        
        controller.updateParticipant("id1");
        
        verify(participantService).updateParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals("id1", persisted.getId());
    }
    
    @Test
    public void getSelfParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder()
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withFirstName("Test").build();
        
        when(participantService.getParticipant(study, ID, false)).thenReturn(studyParticipant);

        Result result = controller.getSelfParticipant();
        assertEquals("application/json", result.contentType());
        
        verify(participantService).getParticipant(study, ID, false);
        
        StudyParticipant deserParticipant = MAPPER.readValue(Helpers.contentAsString(result), StudyParticipant.class);

        assertEquals("Test", deserParticipant.getFirstName());
        assertNull(deserParticipant.getHealthCode());
        assertNull(deserParticipant.getEncryptedHealthCode());
    }
    
    @Test
    public void updateSelfParticipant() throws Exception {
        // All values should be copied over here, also add a healthCode to verify it is not unset.
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(TestUtils.getStudyParticipant(ParticipantControllerTest.class))
                .withHealthCode("healthCode").build();
        
        doReturn(participant).when(participantService).getParticipant(study, ID, false);
        doReturn(new UserSession(participant)).when(authService).updateSession(eq(study), any(), eq(ID));
        
        String json = MAPPER.writeValueAsString(participant);
        mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        JsonNode node = MAPPER.readTree(Helpers.contentAsString(result));
        assertEquals(200, result.status());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        // The session here is returned from the authenticationService, and that is mocked out, but 
        // we verify that we are calling updateSession() with that initialized session (with its 
        // healthCode)
        verify(controller).updateSession(sessionCaptor.capture());
        UserSession session = sessionCaptor.getValue();
        assertEquals("healthCode", session.getHealthCode());
        assertEquals("FirstName", session.getParticipant().getFirstName());
        // etc.
        
        // verify the object is passed to service, one field is sufficient
        verify(cacheProvider).setUserSession(any());
        verify(authService).updateSession(eq(study), any(), eq(ID));
        verify(participantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("FirstName", captured.getFirstName());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, captured.getSharingScope());
        assertTrue(captured.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1"), captured.getDataGroups());
        assertEquals("123-456-7890", captured.getAttributes().get("phone"));
    }
    
    // Some values will be missing in the JSON and should be preserved from this original participant object.
    // This allows client to provide JSON that's less than the entire participant.
    @Test
    public void partialUpdateSelfParticipant() throws Exception {
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("foo", "bar");
        attrs.put("baz", "bap");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("email@email.com")
                .withId("id")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1","group2"))
                .withAttributes(attrs)
                .withLanguages(TestUtils.newLinkedHashSet("en"))
                .withStatus(AccountStatus.DISABLED)
                .withExternalId("POWERS").build();
        doReturn(participant).when(participantService).getParticipant(study, ID, false);
        
        mockPlayContextWithJson(createJson("{'externalId':'simpleStringChange',"+
                "'sharingScope':'no_sharing',"+
                "'notifyByEmail':false,"+
                "'attributes':{'baz':'belgium'},"+
                "'languages':['fr'],"+
                "'status':'enabled',"+
                "'roles':['admin']}"));
        
        Result result = controller.updateSelfParticipant();
        assertEquals(200, result.status());
        JsonNode node = MAPPER.readTree(Helpers.contentAsString(result));
        assertEquals("UserSessionInfo", node.get("type").asText());

        verify(authService).updateSession(eq(study), any(), eq(ID));
        verify(participantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("firstName", captured.getFirstName());
        assertEquals("lastName", captured.getLastName());
        assertEquals("email@email.com", captured.getEmail());
        assertEquals("password", captured.getPassword());
        assertEquals(SharingScope.NO_SHARING, captured.getSharingScope());
        assertFalse(captured.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), captured.getDataGroups());
        assertNull(captured.getAttributes().get("foo"));
        assertEquals("belgium", captured.getAttributes().get("baz"));
        assertEquals(AccountStatus.ENABLED, captured.getStatus());
        assertEquals(Sets.newHashSet("fr"), captured.getLanguages());
        assertEquals("simpleStringChange", captured.getExternalId());
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        Result result = controller.requestResetPassword(ID);
        assertResult(result, 200, "Request to reset password sent to user.");
        
        verify(participantService).requestResetPassword(study, ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotResetPasswordIfNotResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        session.setParticipant(participant);
        
        controller.requestResetPassword(ID);
    }
    
    @Test
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(ID).build();
        doReturn(participant).when(participantService).getParticipant(study, ID, false);
        
        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();
        String json = MAPPER.writeValueAsString(participant);
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        JsonNode node = MAPPER.readTree(Helpers.contentAsString(result));
        assertEquals(200, result.status());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        verify(controller).updateSession(session);
        
        // verify the object is passed to service, one field is sufficient
        verify(participantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
    }
    
    @Test
    public void canGetActivityHistory() throws Exception {
        doReturn(createActivityResults()).when(participantService).getActivityHistory(study, ID, "offsetKey", new Integer(40));
        
        Result result = controller.getActivityHistory(ID, "offsetKey", "40");
        assertEquals(200, result.status());
        PagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result), PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(25, page.getPageSize());
        assertEquals(100, page.getTotal());
        verify(participantService).getActivityHistory(study, ID, "offsetKey", new Integer(40));
    }
    
    @Test
    public void canGetActivityWithNullValues() throws Exception {
        doReturn(createActivityResults()).when(participantService).getActivityHistory(study, ID, null, null);
        
        Result result = controller.getActivityHistory(ID, null, null);
        assertEquals(200, result.status());
        PagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result), PAGED_ACTIVITIES_REF);
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(25, page.getPageSize());
        assertEquals(100, page.getTotal());
        verify(participantService).getActivityHistory(study, ID, null, null);
    }
    
    @Test
    public void deleteActivities() throws Exception {
        Result result = controller.deleteActivities(ID);
        assertResult(result, 200, "Scheduled activities deleted.");
        
        verify(participantService).deleteActivities(study, ID);
    }

    @Test
    public void resendEmailVerification() throws Exception {
        controller.resendEmailVerification(ID);
        
        verify(participantService).resendEmailVerification(study, ID);
    }
    
    @Test
    public void resendConsentAgreement() throws Exception {
        controller.resendConsentAgreement(ID, "subpopGuid");
        
        verify(participantService).resendConsentAgreement(study, SubpopulationGuid.create("subpopGuid"), ID);
    }

    @Test
    public void withdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            TestUtils.mockPlayContextWithJson(json);
            
            controller.withdrawFromAllConsents(ID);
            
            verify(participantService).withdrawAllConsents(study, ID, new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    private PagedResourceList<ScheduledActivity> createActivityResults() {
        List<ScheduledActivity> list = Lists.newArrayList();
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(TestConstants.TEST_1_ACTIVITY);
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        list.add(activity);
        
        return new PagedResourceList<>(list, null, 25, 100);
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return MAPPER.readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
