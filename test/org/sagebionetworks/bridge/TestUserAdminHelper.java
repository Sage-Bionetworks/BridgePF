package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_KEY;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import com.google.common.collect.Lists;

/**
 * A support class that can be injected into any SpringJUnit4ClassRunner test that needs to create a test user before
 * performing some tests. This is an involved process because it goes through our regular UserAdminService class. It
 * requires that your bridge.conf specify the credentials of an existing user with admin privileges (such a user already
 * exists in the local and dev environments; you can get the credentials from Alx).
 */
public class TestUserAdminHelper {

    private static final String PASSWORD = "P4ssword";
    
    UserAdminService userAdminService;
    AuthenticationService authService;
    StudyService studyService;

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public UserSession createUser(String tag) {
        return createUser(tag, Lists.<String>newArrayList());
    }
    
    public UserSession createUser(String tag, List<String> roles) {
        return createUser(tag, roles, true, true);
    }
    
    public UserSession createUser(String tag, List<String> roles, boolean signIn, boolean consent) {
        String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
        if (!tag.contains(prefix)) {
            tag = prefix + tag;
        }
        String[] rolesArray = (roles == null) ? null : roles.toArray(new String[] {});
        SignUp signUp = new SignUp(tag, tag + "@sagebridge.org", PASSWORD, rolesArray);
        Study study = studyService.getStudyByKey(TEST_STUDY_KEY);
        return createUser(signUp, study, signIn, consent);
    }
    
    public UserSession createUser(SignUp signUp, Study study, boolean signIn, boolean consent) {
        return userAdminService.createUser(signUp, study, signIn, consent);
    }

    public void deleteUser(UserSession session, String tag) {
        deleteUser(session);
        String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
        if (!tag.contains(prefix)) {
            tag = prefix + tag;
        }
        userAdminService.deleteUser(tag + "@sagebridge.org");
    }

    public void deleteUser(UserSession session) {
        if (session != null) {
            authService.signOut(session.getSessionToken());
            userAdminService.deleteUser(session.getUser());
        }
    }

    public void deleteUser(Study study, String email) {
        User user = authService.getUser(study, email);
        if (user != null) {
            userAdminService.deleteUser(user);
        }
    }
    
    public String getPassword() {
        return PASSWORD;
    }

    public Study getTestStudy() {
        return studyService.getStudyByKey(TEST_STUDY_KEY);
    }

    public SignIn getSignIn(UserSession session) {
        return new SignIn(session.getUser().getUsername(), PASSWORD);
    }
    
    public SignIn getSignIn(UserSession session, String password) {
        return new SignIn(session.getUser().getUsername(), password);
    }

    public UserProfile getUserProfile(UserSession session) {
        return new UserProfile(session.getUser());
    }
}
