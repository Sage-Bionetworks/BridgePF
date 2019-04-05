package org.sagebionetworks.bridge.play.controllers;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
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
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class SubstudyControllerTest {
    
    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private static final List<Substudy> SUBSTUDIES = ImmutableList.of(Substudy.create(),
            Substudy.create());
    private static final TypeReference<ResourceList<Substudy>> SUBSTUDY_TYPEREF = 
            new TypeReference<ResourceList<Substudy>>() {};
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);

    @Mock
    private SubstudyService service;
    
    @Captor
    private ArgumentCaptor<Substudy> substudyCaptor;
    
    @Spy
    private SubstudyController controller;
    
    private UserSession session;
    
    @Before
    public void before() {
        session = new UserSession();
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        controller.setSubstudyService(service);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void getSubstudiesExcludeDeleted() throws Exception {
        when(service.getSubstudies(TestConstants.TEST_STUDY, false)).thenReturn(SUBSTUDIES);
        
        Result result = controller.getSubstudies("false");
        assertEquals(200, result.status());
        
        ResourceList<Substudy> list = TestUtils.getResponsePayload(result, SUBSTUDY_TYPEREF);
        assertEquals(2, list.getItems().size());
        assertEquals(false, list.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getSubstudies(TestConstants.TEST_STUDY, false);
    }

    @Test
    public void getSubstudiesIncludeDeleted() throws Exception {
        when(service.getSubstudies(TestConstants.TEST_STUDY, true)).thenReturn(SUBSTUDIES);
        
        Result result = controller.getSubstudies("true");
        assertEquals(200, result.status());
        
        ResourceList<Substudy> list = TestUtils.getResponsePayload(result, SUBSTUDY_TYPEREF);
        assertEquals(2, list.getItems().size());
        assertEquals(true, list.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getSubstudies(TestConstants.TEST_STUDY, true);
    }
    
    @Test
    public void getSubstudiesDefaultsToExcludeDeleted() throws Exception {
        when(service.getSubstudies(TestConstants.TEST_STUDY, false)).thenReturn(SUBSTUDIES);
        
        Result result = controller.getSubstudies(null);
        assertEquals(200, result.status());
        
        ResourceList<Substudy> list = TestUtils.getResponsePayload(result, SUBSTUDY_TYPEREF);
        assertEquals(2, list.getItems().size());
        assertEquals(false, list.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getSubstudies(TestConstants.TEST_STUDY, false);
    }
    
    @Test
    public void createSubstudy() throws Exception {
        when(service.createSubstudy(any(), any())).thenReturn(VERSION_HOLDER);
        
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        TestUtils.mockPlay().withBody(substudy).mock();
        
        Result result = controller.createSubstudy();
        assertEquals(201, result.status());

        verify(service).createSubstudy(eq(TestConstants.TEST_STUDY), substudyCaptor.capture());
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals("oneId", persisted.getId());
        assertEquals("oneName", persisted.getName());
        
        VersionHolder returnedValue = TestUtils.getResponsePayload(result, VersionHolder.class);
        assertEquals(VERSION_HOLDER.getVersion(), returnedValue.getVersion());
    }

    @Test
    public void getSubstudy() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        when(service.getSubstudy(TestConstants.TEST_STUDY, "id", true)).thenReturn(substudy);
        
        Result result = controller.getSubstudy("id");
        assertEquals(200, result.status());
        
        Substudy returnedValue = TestUtils.getResponsePayload(result, Substudy.class);
        assertEquals("oneId", returnedValue.getId());
        assertEquals("oneName", returnedValue.getName());

        verify(service).getSubstudy(TestConstants.TEST_STUDY, "id", true);
    }
    
    @Test
    public void updateSubstudy() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        TestUtils.mockPlay().withBody(substudy).mock();
        
        when(service.updateSubstudy(eq(TestConstants.TEST_STUDY), any())).thenReturn(VERSION_HOLDER);
        
        Result result = controller.updateSubstudy("id");
        assertEquals(200, result.status());
        
        VersionHolder returnedValue = TestUtils.getResponsePayload(result, VersionHolder.class);
        assertEquals(VERSION_HOLDER.getVersion(), returnedValue.getVersion());
        
        verify(service).updateSubstudy(eq(TestConstants.TEST_STUDY), substudyCaptor.capture());
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals("oneId", persisted.getId());
        assertEquals("oneName", persisted.getName());
    }
    
    @Test
    public void deleteSubstudyLogical() throws Exception {
        Result result = controller.deleteSubstudy("id", "false");
        TestUtils.assertResult(result, 200, "Substudy deleted.");
        
        verify(service).deleteSubstudy(TestConstants.TEST_STUDY, "id");
    }
    
    @Test
    public void deleteSubstudyPhysical() throws Exception {
        Result result = controller.deleteSubstudy("id", "true");
        TestUtils.assertResult(result, 200, "Substudy deleted.");
        
        verify(service).deleteSubstudyPermanently(TestConstants.TEST_STUDY, "id");
    }

    @Test
    public void deleteSubstudyDefaultsToLogical() throws Exception {
        Result result = controller.deleteSubstudy("id", null);
        TestUtils.assertResult(result, 200, "Substudy deleted.");
        
        verify(service).deleteSubstudy(TestConstants.TEST_STUDY, "id");
    }    

    @Test
    public void deleteSubstudyDeveloperCannotPhysicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        
        Result result = controller.deleteSubstudy("id", "true");
        TestUtils.assertResult(result, 200, "Substudy deleted.");
        
        verify(service).deleteSubstudy(TestConstants.TEST_STUDY, "id");
    }
}
