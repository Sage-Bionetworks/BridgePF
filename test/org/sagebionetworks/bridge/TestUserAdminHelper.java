package org.sagebionetworks.bridge;

import java.util.List;

import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.Study;
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

    private static final String STUDY_HOST = "pd.sagebridge.org";

    UserAdminService userAdminService;
    AuthenticationService authService;
    StudyService studyService;

    private TestUser testUser = new TestUser("tester", "support@sagebase.org", "P4ssword");

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public UserSession createUser() {
        return createUser(testUser);
    }

    public UserSession createUser(TestUser user) {
        Study study = studyService.getStudyByHostname(STUDY_HOST);
        return userAdminService.createUser(testUser.getSignUp(), null, study, true, true);
    }
    
    public UserSession createUser(List<String> roles) {
        Study study = studyService.getStudyByHostname(STUDY_HOST);
        return userAdminService.createUser(testUser.getSignUp(), roles, study, true, true);
    }
    
    public UserSession createUser(TestUser user, List<String> roles, Study study, boolean signIn, boolean consent) {
        return userAdminService.createUser(user.getSignUp(), roles, study, signIn, consent);
    }
    
    public void deleteUser(UserSession session) {
        if (session != null) {
            authService.signOut(session.getSessionToken());
            userAdminService.deleteUser(session.getUser());    
        }
    }

    public Study getStudy() {
        return studyService.getStudyByHostname(STUDY_HOST);
    }

    public SignIn getUserSignIn() {
        return testUser.getSignIn();
    }

    public TestUser getTestUser() {
        return testUser;
    }

    public UserProfile getUserProfile(UserSession session) {
        return new UserProfile(session.getUser());
    }
}
