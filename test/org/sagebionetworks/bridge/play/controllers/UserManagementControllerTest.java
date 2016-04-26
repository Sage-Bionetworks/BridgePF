package org.sagebionetworks.bridge.play.controllers;

import static org.mockito.Matchers.any;
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

import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;

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
        UserSession session = new UserSession();
        User user = new User();
        user.setHealthCode("BBB");
        user.setStudyKey("api");
        user.setRoles(Sets.newHashSet(ADMIN));
        session.setUser(user);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY);
        
        when(authService.getSession(any(String.class))).thenReturn(session);
        when(studyService.getStudy(new StudyIdentifierImpl("api"))).thenReturn(study);
        
        controller.setStudyService(studyService);
        controller.setUserAdminService(userAdminService);
        controller.setAuthenticationService(authService);

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

        assertResult(result, 201, "User created.");
    }
    
    @Test
    public void deleteUser() throws Exception {
        Result result = controller.deleteUser("ASDF");
        
        assertResult(result, 200, "User deleted.");
        verify(userAdminService).deleteUser(study, "ASDF");
    }
}
