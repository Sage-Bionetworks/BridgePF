package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class UserManagementControllerTest {

    @Mock
    AuthenticationService authService;
    
    @Mock
    StudyService studyService;
    
    @Mock
    UserAdminService userAdminService;
    
    @Mock
    Study study;
    
    @Spy
    UserManagementController controller;
    
    @Before
    public void before() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withRoles(Sets.newHashSet(ADMIN))
                .withEmail("email@email.com").build();
                
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(new StudyIdentifierImpl("api"));
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY);
        
        controller.setStudyService(studyService);
        controller.setUserAdminService(userAdminService);
        controller.setAuthenticationService(authService);
        
        doReturn(session).when(userAdminService).createUser(
                anyObject(), anyObject(), anyObject(), anyBoolean(), anyBoolean());
        doReturn(session).when(authService).getSession(any(String.class));
        doReturn(study).when(studyService).getStudy(new StudyIdentifierImpl("api"));

        Map<String,String[]> map = Maps.newHashMap();
        map.put(BridgeConstants.SESSION_TOKEN_HEADER, new String[]{"AAA"});
        
        mockPlayContextWithJson("{}");
        Http.Context context = Http.Context.current.get();
        Http.Request request = context.request();
        when(request.headers()).thenReturn(map);
        doReturn(null).when(controller).getMetrics();
    }
    
    @Test
    public void createdResponseReturnsJSONPayload() throws Exception {
        Result result = controller.createUser();

        assertEquals(201, result.status());
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));

        assertEquals("UserSessionInfo", node.get("type").asText());
        assertEquals("email@email.com", node.get("email").asText());
    }
    
    @Test
    public void deleteUser() throws Exception {
        Result result = controller.deleteUser("ASDF");
        
        assertResult(result, 200, "User deleted.");
        verify(userAdminService).deleteUser(study, "ASDF");
    }
}
