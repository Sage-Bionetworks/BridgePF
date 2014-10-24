package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
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
 * A support class that can be injected into any SpringJUnit4ClassRunner test that needs to 
 * create a test user before performing some tests. After (in a @Before method or a finally 
 * block), call deleteUser() with the TestUser object that was returned, and the user will 
 * be deleted (if there's a session, the session will also be cleaned up).
 */
public class TestUserAdminHelper {

    private static final String PASSWORD = "P4ssword";
    
    UserAdminService userAdminService;
    AuthenticationService authService;
    StudyService studyService;
    
    public class TestUser {
        private final String username;
        private final String email;
        private final String password;
        private final String[] roles;
        private final Study study;
        private final UserSession session;

        public TestUser(String username, String email, String password, List<String> roleList, Study study, UserSession session) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.roles = (roleList == null) ? null : roleList.toArray(new String[roleList.size()]);
            this.study = study;
            this.session = session;
        }
        public SignUp getSignUp() {
            return new SignUp(username, email, password, roles);
        }
        public SignIn getSignIn() {
            return new SignIn(username, password);
        }
        public String getUsername() {
            return username;
        }
        public String getEmail() {
            return email;
        }
        public String getPassword() {
            return password;
        }
        public UserSession getSession() {
            return session;
        }
        public String getSessionToken() {
            return (session == null) ? null : session.getSessionToken();
        }
        public User getUser() {
            return (session == null) ? null : session.getUser();
        }
        public UserProfile getUserProfile() {
            return (session == null) ? null : new UserProfile(session.getUser());
        }
        public Study getStudy() {
            return study;
        }
    }

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public TestUser createUser(String tag) {
        return createUser(tag, Lists.<String>newArrayList());
    }
    
    public TestUser createUser(String tag, List<String> roles) {
        return createUser(tag, roles, true, true);
    }
    
    public TestUser createUser(String tag, List<String> roles, boolean signIn, boolean consent) {
        String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
        if (!tag.contains(prefix)) {
            tag = prefix + tag;
        }
        String[] rolesArray = (roles == null) ? null : roles.toArray(new String[] {});
        SignUp signUp = new SignUp(tag, tag + "@sagebridge.org", PASSWORD, rolesArray);
        Study study = studyService.getStudyByKey(TEST_STUDY_KEY);
        return createUser(signUp, study, signIn, consent);
    }
    
    public TestUser createUser(SignUp signUp, Study study, boolean signIn, boolean consent) {
        checkNotNull(signUp.getUsername());
        checkNotNull(signUp.getEmail());
        checkNotNull(signUp.getPassword());
        checkNotNull(study);
        
        UserSession session = userAdminService.createUser(signUp, study, signIn, consent);
        return new TestUser(signUp.getUsername(), signUp.getEmail(), signUp.getPassword(), signUp.getRoles(), study, session);
    }

    public void deleteUser(TestUser testUser) {
        checkNotNull(testUser);
        
        if (testUser.getSession() != null) {
            // Delete using session if it exists
            authService.signOut(testUser.getSessionToken());
            userAdminService.deleteUser(testUser.getUser());
        } else {
            // Otherwise delete using the user's email
            deleteUser(testUser.getStudy(), testUser.getEmail());
        }
    }
    
    public void deleteUser(Study study, String email) {
        checkNotNull(study);
        checkNotNull(email);
        
        User user = authService.getUser(study, email);
        if (user != null) {
            userAdminService.deleteUser(user);
        }
    }

}
