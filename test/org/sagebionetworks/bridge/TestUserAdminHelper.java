package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A support class that can be injected into any SpringJUnit4ClassRunner test that needs to
 * create a test user before performing some tests. After (in a @Before method or a finally
 * block), call deleteUser() with the TestUser object that was returned, and the user will
 * be deleted (if there's a session, the session will also be cleaned up).
 */
@Component
public class TestUserAdminHelper {

    private static final String PASSWORD = "P4ssword!";
    private static final String EMAIL_DOMAIN = "@sagebridge.org";

    UserAdminService userAdminService;
    AuthenticationService authService;
    StudyService studyService;

    public class TestUser {
        private final String username;
        private final String email;
        private final String password;
        private final Set<Roles> roles;
        private final Set<String> dataGroups;
        private final Study study;
        private final UserSession session;

        public TestUser(SignUp signUp, Study study, UserSession session) {
            this.username = signUp.getUsername();
            this.email = signUp.getEmail();
            this.password = signUp.getPassword();
            this.roles = signUp.getRoles();
            this.roles.add(TEST_USERS);
            this.dataGroups = signUp.getDataGroups();
            this.study = study;
            this.session = session;
        }
        public SignUp getSignUp() {
            return new SignUp(username, email, password, roles, dataGroups);
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
        public Study getStudy() {
            return study;
        }
        public StudyIdentifier getStudyIdentifier() {
            return study.getStudyIdentifier();
        }
    }
    @Autowired
    public final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    @Autowired
    public final void setAuthService(AuthenticationService authService) {
        this.authService = authService;
    }
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public String makeRandomUserName(Class<?> cls) {
        checkNotNull(cls);
        String devPart = BridgeConfigFactory.getConfig().getUser();
        String rndPart = TestUtils.randomName(cls);
        return String.format("bridge-testing+%s-%s", devPart, rndPart);
    }

    public TestUser createUser(Class<?> cls) {
        return createUser(cls, true, true, null, null);
    }
    
    public TestUser createUser(Class<?> cls, Study study, boolean consent) {
        return createUser(cls, study, true, consent, null, null);
    }

    public TestUser createUser(Class<?> cls, boolean signIn, boolean consent) {
        return createUser(cls, signIn, consent, null, null);
    }
    
    public TestUser createUser(Class<?> cls, boolean signIn, boolean consent, Set<Roles> roles, Set<String> dataGroups) {
        Study study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        return createUser(cls, study, signIn, consent, roles, dataGroups);
    }
    
    public TestUser createUser(Class<?> cls, Study study, boolean signIn, boolean consent, Set<Roles> roles, Set<String> dataGroups) {
        String name = makeRandomUserName(cls);
        SignUp signUp = new SignUp(name, name + EMAIL_DOMAIN, PASSWORD, roles, dataGroups);
        return createUser(signUp, study, signIn, consent);
    }

    public TestUser createUser(SignUp signUp, Study study, boolean signIn, boolean consent) {
        checkNotNull(signUp.getUsername());
        checkNotNull(signUp.getEmail());
        checkNotNull(signUp.getPassword());
        checkNotNull(study);

        UserSession session = userAdminService.createUser(signUp, study, signIn, consent);
        return new TestUser(signUp, study, session);
    }

    public void deleteUser(TestUser testUser) {
        checkNotNull(testUser);

        if (testUser.getSession() != null) {
            // Delete using session if it exists
            authService.signOut(testUser.getSession());
            userAdminService.deleteUser(testUser.getStudy(), testUser.getUser().getEmail());
        } else {
            // Otherwise delete using the user's email
            deleteUser(testUser.getStudy(), testUser.getEmail());
        }
    }

    public void deleteUser(Study study, String email) {
        checkNotNull(study);
        checkNotNull(email);

        userAdminService.deleteUser(study, email);
    }

}
