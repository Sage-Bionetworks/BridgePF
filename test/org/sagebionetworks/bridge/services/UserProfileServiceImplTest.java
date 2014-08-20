package org.sagebionetworks.bridge.services;

import javax.annotation.Resource;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import controllers.StudyControllerService;
import static org.junit.Assert.*;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceImplTest {
    
    @Resource
    UserProfileServiceImpl service;
    
    @Resource
    StormPathUserAdminService userAdminService;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    AuthenticationServiceImpl authService;
    
    @Resource
    BridgeConfig bridgeConfig;
    
    private TestUser testUser = new TestUser("testUser", "testUser@sagebridge.org", "P4ssword");
    private Study study;
    private UserSession adminUserSession;
    private UserSession userSession;

    @Before
    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        TestUser admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminUserSession = authService.signIn(study, admin.getSignIn());

        userSession = userAdminService.createUser(adminUserSession.getUser(), testUser.getSignUp(), study, true, true);
    }
    
    @After
    public void after() {
        userAdminService.deleteUser(adminUserSession.getUser(), userSession.getUser(), study);
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile userProfile = testUser.getUserProfile(null);
        userProfile.setFirstName("Test");
        userProfile.setLastName("Powers");
        
        User updatedUser = service.updateProfile(userSession.getUser(), userProfile);
        
        assertTrue(userSession.getUser().equals(updatedUser));
    }

}
