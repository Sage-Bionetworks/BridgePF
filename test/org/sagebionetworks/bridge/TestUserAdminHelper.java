package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

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
            this.study = study;
            this.session = session;

            if (roleList == null) {
                this.roles = new String[1];
                this.roles[0] = "test_users";
            } else {
                if (!roleList.contains("test_users")) {
                    roleList.add("test_users");
                }
                this.roles = roleList.toArray(new String[roleList.size()]);
            }

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

    public String makeRandomUserName(Class<?> cls) {
        String clsPart = cls.getSimpleName();
        String devPart = BridgeConfigFactory.getConfig().getUser();
        String rndPart = TestUtils.randomName();
        return String.format("%s-%s-%s", devPart, clsPart, rndPart);
    }

    public TestUser createUser(Class<?> cls) {
        return createUser(cls, (String[])null);
    }

    public TestUser createUser(Class<?> cls, String... roles) {
        checkNotNull(cls, "Class must not be null");

        return createUser(cls, true, true, roles);
    }

    public TestUser createUser(Class<?> cls, boolean signIn, boolean consent, String...roles) {
        checkNotNull(cls, "Class must not be null");

        String name = makeRandomUserName(cls);
        SignUp signUp = new SignUp(name, name + "@sagebridge.org", PASSWORD, roles);
        Study study = studyService.getStudyByIdentifier(TEST_STUDY_IDENTIFIER);
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
