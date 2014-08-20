package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.UserAdminService;

import controllers.StudyControllerService;

public class TestUserAdminHelper {

    private UserAdminService userAdminService;
    private AuthenticationService authService;
    private BridgeConfig bridgeConfig;
    private StudyControllerService studyControllerService;
    
    private TestUser testUser = new TestUser("tester", "tester@sagebase.org", "P4ssword");
    private Study study;
    private UserSession adminSession;
    private UserSession userSession;
    
    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    public void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }
    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminSession = authService.signIn(study, admin);

        userSession = userAdminService.createUser(adminSession.getUser(), testUser.getSignUp(), study, true, true);
    }
    
    public void after() {
        userAdminService.deleteUser(adminSession.getUser(), userSession.getUser(), study);
    }
    
    public User getUser() {
        return userSession.getUser();
    }
    
    public String getUserSession() {
        return userSession.getSessionToken();
    }
    
    public TestUser getTestUser() {
        return testUser;
    }
}
