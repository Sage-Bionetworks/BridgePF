package org.sagebionetworks.bridge.play.controllers;

import static play.test.Helpers.contentAsString;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

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
        
        verify(optionsService).setString(TEST_STUDY, "healthCode", EXTERNAL_IDENTIFIER, "ABC-123-XYZ");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void validDataGroupsCanBeAdded() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"group1\"]}"));
        
        UserProfileController controller = controllerForExternalIdTests();
        
        Result result = controller.updateDataGroups();
        
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), captor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(Sets.newHashSet("group1"), dataGroups);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }
    
    @SuppressWarnings({"unchecked"})
    @Test
    public void invalidDataGroupsRejected() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"completelyInvalidGroup\"]}"));
        UserProfileController controller = controllerForExternalIdTests();
        try {
            controller.updateDataGroups();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("DataGroups is invalid"));
            verify(optionsService, never()).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), any(Set.class));
        }
    }

    @Test
    public void canGetDataGroups() throws Exception {
        Set<String> dataGroupsSet = Sets.newHashSet("group1","group2");
        
        UserProfileController controller = controllerForExternalIdTests();
        
        when(optionsService.getStringSet("healthCode", DATA_GROUPS)).thenReturn(dataGroupsSet);
        
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
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), captor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(Sets.newHashSet(), dataGroups);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }
    
    private UserSession mockSession() {
        User user = new User();
        user.setHealthCode("healthCode");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(TEST_STUDY);
        return session;
    }
    
    private UserProfileController controllerForExternalIdTests() {
        Study study = new DynamoStudy();
        study.setDataGroups(Sets.newHashSet("group1", "group2"));
        
        optionsService = mock(ParticipantOptionsService.class);
        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy((StudyIdentifier)any())).thenReturn(study);
        
        UserProfileController controller = spy(new UserProfileController());
        controller.setStudyService(studyService);
        controller.setParticipantOptionsService(optionsService);
        doReturn(mockSession()).when(controller).getAuthenticatedSession();

        return controller;
    }
}
