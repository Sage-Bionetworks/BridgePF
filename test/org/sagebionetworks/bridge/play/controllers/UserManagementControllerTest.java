package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static play.test.Helpers.contentAsString;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;

public class UserManagementControllerTest {

    UserManagementController controller;
    
    @Before
    public void before() throws Exception {
        UserSession session = new UserSession();
        User user = new User();
        user.setHealthCode("BBB");
        user.setStudyKey("api");
        user.setRoles(Sets.newHashSet(ADMIN));
        session.setUser(user);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY);
        
        AuthenticationService authService = mock(AuthenticationService.class);
        when(authService.getSession(any(String.class))).thenReturn(session);
        StudyService studyService = mock(StudyService.class);
        UserAdminService userAdminService = mock(UserAdminService.class);
        
        // Using a spy on the controller, even though it's under test, because there's a reference
        // to Cache in BaseController which is a Play class with a static method.
        controller = spy(new UserManagementController());
        controller.setStudyService(studyService);
        controller.setUserAdminService(userAdminService);
        controller.setAuthenticationService(authService);

        Map<String,String[]> map = Maps.newHashMap();
        map.put(BridgeConstants.SESSION_TOKEN_HEADER, new String[]{"AAA"});
        
        TestUtils.mockPlayContextWithJson("{}");
        Http.Context context = Http.Context.current.get();
        Http.Request request = context.request();
        when(request.headers()).thenReturn(map);
        doReturn(null).when(controller).getMetrics();
    }
    
    @Test
    public void createdResponseReturnsJSONPayload() throws Exception {
        Result result = controller.createUser();
        
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));
        
        // This is the one assertion in this test... Play is hard to test.
        assertEquals("User created.", node.get("message").asText());
    }
    
}
