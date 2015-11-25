package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class SubpopulationControllerTest {

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("test-key");
    
    @Spy
    private SubpopulationController controller;
    
    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private UserSession session;
    
    @Mock
    private Study study;
    
    @Captor
    private ArgumentCaptor<Subpopulation> captor;
    
    @Before
    public void before() throws Exception {
        User user = new User();
        user.setRoles(Sets.newHashSet(Roles.DEVELOPER));
        
        controller.setSubpopulationService(subpopService);
        controller.setStudyService(studyService);
        
        when(study.getStudyIdentifier()).thenReturn(STUDY_IDENTIFIER);
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(user).when(session).getUser();
        doReturn(STUDY_IDENTIFIER).when(session).getStudyIdentifier();
        when(studyService.getStudy(STUDY_IDENTIFIER)).thenReturn(study);
    }

    @Test
    public void getAllSubpopulations() throws Exception {
        Http.Context context = TestUtils.mockPlayContext();
        Http.Context.current.set(context);
        
        Result result = controller.getAllSubpopulations();
        
        assertEquals(200, result.status());
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertNotNull(node.get("items"));
        assertNotNull(node.get("total"));
        assertEquals("ResourceList", node.get("type").asText());
        
        verify(studyService).getStudy(STUDY_IDENTIFIER);
        verify(subpopService).getSubpopulations(study);
    }
    
    @Test
    public void createSubpopulation() throws Exception {
        String json = "{\"name\":\"Name\",\"description\":\"Description\",\"required\":true,\"minAppVersion\":2,\"maxAppVersion\":10,\"allOfGroups\":[\"requiredGroup\"],\"noneOfGroups\":[\"prohibitedGroup\"]}";
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        DynamoSubpopulation createdSubpop = new DynamoSubpopulation();
        createdSubpop.setGuid("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(subpopService).createSubpopulation(eq(study), captor.capture());

        Result result = controller.createSubpopulation();
        assertEquals(201, result.status());
        String responseJSON = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(responseJSON);
        assertEquals("AAA", node.get("guid").asText());
        assertEquals(1L, node.get("version").asLong());
        assertEquals("GuidVersionHolder", node.get("type").asText());
        
        Subpopulation created = captor.getValue();
        assertEquals("Name", created.getName());
        assertEquals("Description", created.getDescription());
        assertTrue(created.isRequired());
        assertEquals((Integer)2, created.getMinAppVersion());
        assertEquals((Integer)10, created.getMaxAppVersion());
        assertEquals(Sets.newHashSet("requiredGroup"), created.getAllOfGroups());
        assertEquals(Sets.newHashSet("prohibitedGroup"), created.getNoneOfGroups());
    }
    
    @Test
    public void updateSubpopulation() throws Exception {
        String json = "{\"name\":\"Name\",\"description\":\"Description\",\"required\":true,\"minAppVersion\":2,\"maxAppVersion\":10,\"allOfGroups\":[\"requiredGroup\"],\"noneOfGroups\":[\"prohibitedGroup\"]}";
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        DynamoSubpopulation createdSubpop = new DynamoSubpopulation();
        createdSubpop.setGuid("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(subpopService).updateSubpopulation(eq(study), captor.capture());

        Result result = controller.updateSubpopulation("AAA");
        assertEquals(200, result.status());
        String responseJSON = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(responseJSON);
        assertEquals("AAA", node.get("guid").asText());
        assertEquals(1L, node.get("version").asLong());
        assertEquals("GuidVersionHolder", node.get("type").asText());
        
        Subpopulation created = captor.getValue();
        assertEquals("AAA", created.getGuid());
        assertEquals("Name", created.getName());
        assertEquals("Description", created.getDescription());
        assertTrue(created.isRequired());
        assertEquals((Integer)2, created.getMinAppVersion());
        assertEquals((Integer)10, created.getMaxAppVersion());
        assertEquals(Sets.newHashSet("requiredGroup"), created.getAllOfGroups());
        assertEquals(Sets.newHashSet("prohibitedGroup"), created.getNoneOfGroups());
    }
    
    @Test
    public void getSubpopulation() throws Exception {
        Http.Context context = TestUtils.mockPlayContext();
        Http.Context.current.set(context);
        
        Subpopulation subpop = new DynamoSubpopulation();
        subpop.setGuid("AAA");
        doReturn(subpop).when(subpopService).getSubpopulation(STUDY_IDENTIFIER, "AAA");
        
        Result result = controller.getSubpopulation("AAA");
        
        // Serialization has been tested elsewhere, we're not testing it all here, we're just
        // verifying the object is returned in the API
        assertEquals(200, result.status());
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals("Subpopulation", node.get("type").asText());
        assertEquals("AAA", node.get("guid").asText());
        
        verify(subpopService).getSubpopulation(STUDY_IDENTIFIER, "AAA");
    }
    
    @Test
    public void deleteSubpopulation() throws Exception {
        Http.Context context = TestUtils.mockPlayContext();
        Http.Context.current.set(context);

        Result result = controller.deleteSubpopulation("AAA");
        assertEquals(200, result.status());
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals("Subpopulation has been deleted.", node.get("message").asText());
        
        verify(subpopService).deleteSubpopulation(STUDY_IDENTIFIER, "AAA");
    }
    
}
