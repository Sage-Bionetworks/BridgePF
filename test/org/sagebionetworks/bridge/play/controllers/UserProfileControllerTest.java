package org.sagebionetworks.bridge.play.controllers;

import static play.test.Helpers.contentAsString;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.play.controllers.UserProfileController;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantOptionsServiceImpl;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

public class UserProfileControllerTest {

    private ParticipantOptionsService optionsService;
    
    @Test
    public void canSubmitExternalIdentifier() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"identifier\":\"ABC-123-XYZ\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();
                
        Result result = controller.createExternalIdentifier();
        assertEquals(200, result.status());
        assertEquals("application/json", result.contentType());
        assertEquals("{\"message\":\"External identifier added to user profile.\"}", contentAsString(result));
        
        verify(optionsService).setExternalIdentifier(TEST_STUDY, "healthCode", "ABC-123-XYZ");
    }
    
    @Test
    public void externalIdentifierVerifiesIdentifierExists() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"identifier\":\"\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();

        try {
            controller.createExternalIdentifier();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier is not valid.", e.getMessage());
        }
        verifyNoMoreInteractions(optionsService);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void validDataGroupsCanBeAdded() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"group1\"]}"));
        
        UserProfileController controller = controllerForExternalIdTests();
        
        Result result = controller.updateDataGroups();
        
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(optionsService).setDataGroups(any(), any(), captor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(Sets.newHashSet("group1"), dataGroups);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }

    @Test
    public void invalidDataGroupsThrowException() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"A\",\"B\",\"C\"]}"));
        
        UserProfileController controller = controllerForExternalIdTests();
        try {
            controller.updateDataGroups();
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("dataGroups 'A' is not one of these valid values: "));
            assertTrue(e.getMessage().contains("dataGroups 'B' is not one of these valid values: "));
            assertTrue(e.getMessage().contains("dataGroups 'C' is not one of these valid values: "));
            verifyNoMoreInteractions(optionsService);
        }
    }
    
    @Test
    public void canGetDataGroups() throws Exception {
        Set<String> dataGroupsSet = Sets.newHashSet("group1","group2");
        
        UserProfileController controller = controllerForExternalIdTests();
        when(optionsService.getDataGroups("healthCode")).thenReturn(dataGroupsSet);
        
        Result result = controller.getDataGroups();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals("DataGroups", node.get("type").asText());
        ArrayNode array = (ArrayNode)node.get("dataGroups");
        assertEquals(2, array.size());
        for (int i=0; i < array.size(); i++) {
            dataGroupsSet.contains(array.get(i).asText());
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void evenEmptyJsonActsOK() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{}"));
        
        UserProfileController controller = controllerForExternalIdTests();
        
        Result result = controller.updateDataGroups();
        
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(optionsService).setDataGroups(any(), any(), captor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(Sets.newHashSet(), dataGroups);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());

    }
    
    private UserSession mockSession() {
        User user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(TEST_STUDY);
        return session;
    }
    
    private UserProfileController controllerForExternalIdTests() {
        Study study = new DynamoStudy();
        study.setDataGroups(Sets.newHashSet("group1", "group2"));
        
        optionsService = mock(ParticipantOptionsServiceImpl.class);
        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy((StudyIdentifier)any())).thenReturn(study);
        
        UserProfileController controller = spy(new UserProfileController());
        controller.setStudyService(studyService);
        controller.setParticipantOptionsService(optionsService);
        doReturn(mockSession()).when(controller).getAuthenticatedSession();

        return controller;
    }
}
