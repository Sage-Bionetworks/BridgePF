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
import org.sagebionetworks.bridge.models.accounts.StudyParticipant2;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

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
    private ArgumentCaptor<Map<ParticipantOption,String>> captor;
    
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
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(summaries, 10, 20, 30);
        
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
        
        //verify paging
        assertEquals(10, page.getOffsetBy());
        assertEquals(20, page.getPageSize());
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
        StudyParticipant2 studyParticipant = new StudyParticipant2.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, "email@email.com")).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant("email@email.com");
        String string = Helpers.contentAsString(result);
        StudyParticipant2 retrievedParticipant = BridgeObjectMapper.get().readValue(string, StudyParticipant2.class);
        // Verify that there's a field, full serialization tested in StudyParticipant2Test
        assertEquals("Test", retrievedParticipant.getFirstName());
        
        verify(participantService).getParticipant(STUDY, "email@email.com");
    }
    
    @Test
    public void updateParticipantOptions() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,'externalId':'abcd','dataGroups':['group1','group2'],"+
                "'languages':['en','fr']}"));        
        
        Result result = controller.updateParticipantOptions("email@email.com");
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Participant options updated.", node.get("message").asText());
        assertEquals(200, result.status());
        
        verify(participantService).updateParticipantOptions(eq(STUDY), eq("email@email.com"), (Map<ParticipantOption,String>)captor.capture());
        Map<ParticipantOption,String> options = captor.getValue();
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS.name(), options.get(SHARING_SCOPE));
        assertEquals(Boolean.TRUE.toString(), options.get(ParticipantOption.EMAIL_NOTIFICATIONS));
        assertEquals("abcd", options.get(EXTERNAL_IDENTIFIER));
        assertTrue(options.get(DATA_GROUPS).contains("group1"));
        assertTrue(options.get(DATA_GROUPS).contains("group2"));
        assertTrue(options.get(LANGUAGES).contains("en"));
        assertTrue(options.get(LANGUAGES).contains("fr"));
    }
    
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
