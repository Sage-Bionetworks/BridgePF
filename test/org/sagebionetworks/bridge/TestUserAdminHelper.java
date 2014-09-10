package org.sagebionetworks.bridge;

import java.util.List;

import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

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
    StudyService studyService;

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

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public void createOneUser() {
        createOneUser(null);
    }

    public void createOneUser(List<String> roles) {
        study = studyService.getStudyByHostname("pd.sagebridge.org");
        SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminSession = authService.signIn(study, admin);

        userSession = userAdminService.createUser(testUser.getSignUp(), roles, study, true, true);
    }
    
    public void deleteOneUser() {
        userAdminService.deleteUser(userSession.getUser());

        authService.signOut(adminSession.getSessionToken());
    }

    public UserSession createUser(TestUser user, List<String> roles, boolean signIn, boolean consent) {
        return userAdminService.createUser(user.getSignUp(), roles, study, signIn, consent);
    }

    public void deleteUser(User user) {
        userAdminService.deleteUser(user);
    }
    
    public UserSession createUserWithoutConsentOrSignIn(TestUser user, List<String> roles) {
        if (study == null) {
            study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        }
        if (adminSession == null) {
            SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
            adminSession = authService.signIn(study, admin);
        }
        return userAdminService.createUser(adminSession.getUser(), user.getSignUp(), roles, study, false, false);
        
    }
    
    public UserSession createUserWithoutConsent(TestUser user, List<String> roles) {
        if (study == null) {
            study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        }
        if (adminSession == null) {
            SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
            adminSession = authService.signIn(study, admin);
        }
        return userAdminService.createUser(adminSession.getUser(), user.getSignUp(), roles, study, true, false);
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

}
