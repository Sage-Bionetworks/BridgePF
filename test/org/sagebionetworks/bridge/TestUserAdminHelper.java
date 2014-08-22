package org.sagebionetworks.bridge;

import java.util.Set;

import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.cache.CacheProvider;
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
 * A support class that can be injected into any SpringJUnit4ClassRunner test
 * that needs to create a test user before performing some tests. This is an
 * involved process because it goes through our regular UserAdminService class.
 * It requires that your bridge.conf specify the credentials of an existing user
 * with admin privileges (such a user already exists in the local and dev
 * environments; you can get the credentials from Alx).
 */
public class TestUserAdminHelper {

    UserAdminService userAdminService;
    AuthenticationService authService;
    BridgeConfig bridgeConfig;
    StudyControllerService studyControllerService;
    CacheProvider cache;

    private TestUser testUser = new TestUser("tester", "tester@sagebase.org", "P4ssword");
    private Study study;
    private UserSession adminSession;
    private String userSessionToken;

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

    public void setCacheProvider(CacheProvider cache) {
        this.cache = cache;
    }

    public void createOneUser() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        SignIn admin = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminSession = authService.signIn(study, admin);

        UserSession userSession = userAdminService.createUser(adminSession.getUser(), testUser.getSignUp(), study,
                true, true);
        userSessionToken = userSession.getSessionToken();
        cache.setUserSession(userSessionToken, userSession);
    }

    public void deleteOneUser() {
        userAdminService.deleteUser(adminSession.getUser(), cache.getUserSession(userSessionToken).getUser());

        authService.signOut(adminSession.getSessionToken());
    }

    public Study getStudy() {
        return study;
    }

    public User getUser() {
        return cache.getUserSession(userSessionToken).getUser();
    }

    public SignIn getUserSignIn() {
        return testUser.getSignIn();
    }

    public String getUserSessionToken() {
        return cache.getUserSession(userSessionToken).getSessionToken();
    }

    public UserProfile getUserProfile() {
        return new UserProfile(cache.getUserSession(userSessionToken).getUser());
    }

    public TestUser getTestUser() {
        return testUser;
    }

    public void addRoleToUser(String role) {
        User user = cache.getUserSession(userSessionToken).getUser();

        role = role.toLowerCase();
        Set<String> roles = user.getRoles();
        if (!roles.contains(role)) {
            roles.add(role);
        }

        user.setRoles(roles);
        UserSession session = cache.getUserSession(userSessionToken);
        session.setUser(user);
        cache.setUserSession(userSessionToken, session);
    }

    public void removeRoleFromUser(String role) {
        User user = cache.getUserSession(userSessionToken).getUser();

        role = role.toLowerCase();
        Set<String> roles = user.getRoles();
        if (roles.contains(role)) {
            roles.remove(role);
        }

        user.setRoles(roles);
        UserSession session = cache.getUserSession(userSessionToken);
        session.setUser(user);
        cache.setUserSession(userSessionToken, session);
    }

    public UserSession createUserWithoutConsentOrSignIn(TestUser user) {
        return userAdminService.createUser(adminSession.getUser(), user.getSignUp(), study, false, false);
    }

    public void deleteUser(User user) {
        userAdminService.deleteUser(adminSession.getUser(), user);
    }
}
