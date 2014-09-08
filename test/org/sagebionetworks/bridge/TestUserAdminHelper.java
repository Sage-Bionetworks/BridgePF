package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.UserAdminService;

import controllers.StudyControllerService;

/**
 * A support class that can be injected into any SpringJUnit4ClassRunner test that needs to create a test user before
 * performing some tests. This is an involved process because it goes through our regular UserAdminService class. It
 * requires that your bridge.conf specify the credentials of an existing user with admin privileges (such a user already
 * exists in the local and dev environments; you can get the credentials from Alx).
 */
public class TestUserAdminHelper {

    UserAdminService userAdminService;
    AuthenticationService authService;
    BridgeConfig bridgeConfig;
    StudyControllerService studyControllerService;

    private TestUser testUser = new TestUser("tester", "support@sagebase.org", "P4ssword");
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

    public void createOneUser() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminSession = authService.signIn(study, admin);

        userSession = userAdminService.createUser(adminSession.getUser(), testUser.getSignUp(), study, true, true);
    }

    public void deleteOneUser() {
        userAdminService.deleteUser(adminSession.getUser(), userSession.getUser());

        authService.signOut(adminSession.getSessionToken());
    }

    public Study getStudy() {
        return study;
    }

    public User getAdminUser() {
        return adminSession.getUser();
    }

    public String getAdminSessionToken() {
        return adminSession.getSessionToken();
    }

    public User getUser() {
        return userSession.getUser();
    }

    public SignIn getUserSignIn() {
        return testUser.getSignIn();
    }

    public String getUserSessionToken() {
        return userSession.getSessionToken();
    }

    public UserProfile getUserProfile() {
        return new UserProfile(userSession.getUser());
    }

    public TestUser getTestUser() {
        return testUser;
    }

    public UserSession createUserWithoutConsentOrSignIn(TestUser user) {
        if (study == null) {
            study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        }
        if (adminSession == null) {
            SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
            adminSession = authService.signIn(study, admin);
        }
        return userAdminService.createUser(adminSession.getUser(), user.getSignUp(), study, false, false);
        
    }

    public void deleteUser(User user) {
        userAdminService.deleteUser(adminSession.getUser(), user);
    }
}
