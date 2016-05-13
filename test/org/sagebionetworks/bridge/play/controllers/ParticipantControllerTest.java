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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
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

    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(Roles.RESEARCHER);
    
    private static final String ID = "ASDF";

    private static final String EMAIL = "email@email.com";

    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};

    private static final AccountSummary SUMMARY = new AccountSummary("firstName", "lastName", "email", "id",
            DateTime.now(), AccountStatus.ENABLED, TestConstants.TEST_STUDY);
    
    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setUserProfileAttributes(Sets.newHashSet("foo","baz"));
        STUDY.setIdentifier("test-study");
    }
    
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
    
    private UserSession session;
    
    @Before
    public void before() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(CALLER_ROLES)
                .withId(ID).build();
        
        session = new UserSession(participant);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.RESEARCHER);
        doReturn(session).when(controller).getAuthenticatedSession();
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, 10, 20, 30).withFilter("emailFilter", "foo");
        
        when(authService.updateSession(eq(STUDY), any(), eq(ID))).thenReturn(session);
        
        when(participantService.getPagedAccountSummaries(eq(STUDY), anyInt(), anyInt(), any())).thenReturn(page);
        
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authService);
        controller.setCacheProvider(cacheProvider);
        
        TestUtils.mockPlayContext();
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
        verify(participantService).getPagedAccountSummaries(STUDY, 10, 20, "foo");
    }
    
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null);
        
        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, API_DEFAULT_PAGE_SIZE, null);
    }

    @Test
    public void getParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, CALLER_ROLES, ID)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        String string = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = BridgeObjectMapper.get().readValue(string, StudyParticipant.class);
        // Verify that there's a field, full serialization tested in StudyParticipant2Test
        assertEquals("Test", retrievedParticipant.getFirstName());
        
        verify(participantService).getParticipant(STUDY, CALLER_ROLES, ID);
    }
    
    @Test
    public void signUserOut() throws Exception {
        controller.signOut(ID);
        
        verify(participantService).signUserOut(STUDY, ID);
    }

    @Test
    public void updateParticipant() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'id':'junkId','attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant(ID);
        assertResult(result, 200, "Participant updated.");
        
        verify(participantService).updateParticipant(eq(STUDY), eq(CALLER_ROLES), participantCaptor.capture());
        verify(authService).updateSession(eq(STUDY), any(), eq(ID));
        
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
        // the ID in the URL, not the one in the JSON (which can be wrong or missing, we don't care)
        assertEquals(ID, participant.getId()); 
    }
    
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder("ABCD");
        doReturn(holder).when(participantService).createParticipant(eq(STUDY), any(), any(StudyParticipant.class), eq(true));
        
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.createParticipant();
        
        assertEquals(201, result.status());
        String id = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(participantService).createParticipant(eq(STUDY), eq(CALLER_ROLES), participantCaptor.capture(), eq(true));
        
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
    public void getSelfParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, NO_CALLER_ROLES, ID)).thenReturn(studyParticipant);

        Result result = controller.getSelfParticipant();
        
        verify(participantService).getParticipant(STUDY, NO_CALLER_ROLES, ID);
        
        StudyParticipant deserParticipant = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), StudyParticipant.class);

        assertEquals("Test", deserParticipant.getFirstName());
    }
    
    @Test
    public void updateSelfParticipant() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        doReturn(participant).when(participantService).getParticipant(STUDY, NO_CALLER_ROLES, ID);
        
        String json = BridgeObjectMapper.get().writeValueAsString(participant);
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(200, result.status());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        verify(controller).updateSession(session);
        
        // verify the object is passed to service, one field is sufficient
        verify(cacheProvider).setUserSession(any());
        verify(authService).updateSession(eq(STUDY), any(), eq(ID));
        verify(participantService).updateParticipant(eq(STUDY), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
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
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1","group2"))
                .withAttributes(attrs)
                .withLanguages(TestUtils.newLinkedHashSet("en"))
                .withStatus(AccountStatus.DISABLED)
                .withExternalId("POWERS").build();
        doReturn(participant).when(participantService).getParticipant(STUDY, NO_CALLER_ROLES, ID);
        
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'externalId':'simpleStringChange',"+
                "'sharingScope':'no_sharing','notifyByEmail':false,'attributes':{'baz':'belgium'},"+
                "'languages':['fr'],'status':'enabled','roles':['admin']}"));
        
        Result result = controller.updateSelfParticipant();
        assertEquals(200, result.status());
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("UserSessionInfo", node.get("type").asText());

        verify(authService).updateSession(eq(STUDY), any(), eq(ID));
        verify(participantService).updateParticipant(eq(STUDY), eq(NO_CALLER_ROLES), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals("firstName", captured.getFirstName());
        assertEquals("lastName", captured.getLastName());
        assertEquals("email@email.com", captured.getEmail());
        assertEquals(ID, captured.getId());
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
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(ID).build();
        doReturn(participant).when(participantService).getParticipant(STUDY, NO_CALLER_ROLES, ID);
        
        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();
        String json = BridgeObjectMapper.get().writeValueAsString(participant);
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(200, result.status());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        verify(controller).updateSession(session);
        
        // verify the object is passed to service, one field is sufficient
        verify(participantService).updateParticipant(eq(STUDY), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
