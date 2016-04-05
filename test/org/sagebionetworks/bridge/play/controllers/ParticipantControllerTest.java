package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {
    
    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};

    private static final AccountSummary SUMMARY = new AccountSummary("firstName","lastName","email",AccountStatus.ENABLED);
    
    private static final Study STUDY = new DynamoStudy();
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private StudyService studyService;
    
    @Captor
    private ArgumentCaptor<Map<ParticipantOption,String>> optionMapCaptor;

    @Captor
    private ArgumentCaptor<UserProfile> profileCaptor;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Before
    public void before() throws Exception {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.RESEARCHER);
        
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, 10, 20, 30).withFilter("emailFilter", "foo");
        
        when(participantService.getPagedAccountSummaries(eq(STUDY), anyInt(), anyInt(), any())).thenReturn(page);
        
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);
        
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
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void getParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, "email@email.com")).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant("email@email.com");
        String string = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = BridgeObjectMapper.get().readValue(string, StudyParticipant.class);
        // Verify that there's a field, full serialization tested in StudyParticipant2Test
        assertEquals("Test", retrievedParticipant.getFirstName());
        
        verify(participantService).getParticipant(STUDY, "email@email.com");
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void updateParticipantOptions() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,'externalId':'abcd','dataGroups':['group1','group2'],"+
                "'languages':['en','fr']}"));        
        
        Result result = controller.updateParticipantOptions("email@email.com");
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Participant options updated.", node.get("message").asText());
        assertEquals(200, result.status());
        
        verify(participantService).updateParticipantOptions(eq(STUDY), eq("email@email.com"), optionMapCaptor.capture());
        Map<ParticipantOption,String> options = optionMapCaptor.getValue();
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS.name(), options.get(SHARING_SCOPE));
        assertEquals(Boolean.TRUE.toString(), options.get(ParticipantOption.EMAIL_NOTIFICATIONS));
        assertEquals("abcd", options.get(EXTERNAL_IDENTIFIER));
        assertTrue(options.get(DATA_GROUPS).contains("group1"));
        assertTrue(options.get(DATA_GROUPS).contains("group2"));
        assertTrue(options.get(LANGUAGES).contains("en"));
        assertTrue(options.get(LANGUAGES).contains("fr"));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void updateParticipantProfile() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        
        TestUtils.mockPlayContextWithJson(TestUtils.createJson(
            "{'firstName':'new first name','lastName':'new last name','phone':'new attribute'}"));
        
        Result result = controller.updateProfile("email@email.com");
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        String message = node.get("message").asText();
        
        assertEquals("User profile updated.", message);
        
        verify(participantService).updateProfile(eq(STUDY), eq("email@email.com"), profileCaptor.capture());
        UserProfile capturedProfile = profileCaptor.getValue();
        
        assertEquals("new first name", capturedProfile.getFirstName());
        assertEquals("new last name", capturedProfile.getLastName());
        assertEquals("new attribute", capturedProfile.getAttribute(("phone")));
    }
    
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void signUserOut() throws Exception {
        controller.signOut("email@email.com");
        
        verify(participantService).signUserOut(STUDY, "email@email.com");
    }

    @Test(expected = BadRequestException.class)
    public void getParticipantNoEmail() {
        controller.getParticipant(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = BadRequestException.class)
    public void updateParticipantOptionsNoEmail() {
        controller.updateParticipantOptions(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = BadRequestException.class)
    public void updateProfileNoEmail() {
        controller.updateProfile(null);
    }
    
    @Test(expected = BadRequestException.class)
    public void signOutNoEmail() throws Exception {
        controller.signOut(null);
    }

    @Test(expected = BadRequestException.class)
    public void getParticipantBlank() {
        controller.getParticipant("");
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = BadRequestException.class)
    public void updateParticipantOptionsBlank() {
        controller.updateParticipantOptions("  ");
    }
    
    @SuppressWarnings("deprecation")
    @Test(expected = BadRequestException.class)
    public void updateProfileBlank() {
        controller.updateProfile("\t");
    }
    
    @Test(expected = BadRequestException.class)
    public void signOutBlank() throws Exception {
        controller.signOut("");
    }
    
    @Test
    public void createParticipant() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.createParticipant();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals(201, result.status());
        assertEquals("Participant created.", node.get("message").asText());
        
        verify(participantService).createParticipant(eq(STUDY), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals("email@email.com", participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }

    @Test
    public void updateParticipant() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant("email@email.com");
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals(200, result.status());
        assertEquals("Participant updated.", node.get("message").asText());
        
        verify(participantService).updateParticipant(eq(STUDY), eq("email@email.com"), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals("email@email.com", participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantMissing() {
        controller.updateParticipant(null);
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantBlank() {
        controller.updateParticipant("");
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantRequiresEmailMatch() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'email':'email@email.com'}"));
        
        controller.updateParticipant("email-different@email.com");
    }

    @Test
    public void updateParticipantNoJsonEmailOK() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{}"));
        
        controller.updateParticipant("email-different@email.com");
        verify(participantService).updateParticipant(eq(STUDY), eq("email-different@email.com"), any());
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
