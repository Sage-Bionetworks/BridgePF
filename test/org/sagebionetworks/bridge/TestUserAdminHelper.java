package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Set;

import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import com.google.common.collect.Sets;

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

    UserAdminService userAdminService;
    AuthenticationService authService;
    StudyService studyService;

    public class TestUser {
        private final String email;
        private final String password;
        private final Set<Roles> roles;
        private final Set<String> dataGroups;
        private final Study study;
        private final UserSession session;

        public TestUser(SignUp signUp, Study study, UserSession session) {
            this.email = signUp.getEmail();
            this.password = signUp.getPassword();
            this.roles = Sets.newHashSet(signUp.getRoles());
            this.roles.add(TEST_USERS);
            this.dataGroups = signUp.getDataGroups();
            this.study = study;
            this.session = session;
        }
        public SignUp getSignUp() {
            return new SignUp(email, password, roles, dataGroups);
        }
        public SignIn getSignIn() {
            return new SignIn(email, password);
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
        public String getId() {
            return (session == null) ? null : session.getUser().getId();
        }
        public StudyIdentifier getStudyIdentifier() {
            return study.getStudyIdentifier();
        }
        public CriteriaContext getCriteriaContext() {
            return new CriteriaContext.Builder()
                .withLanguages(session.getUser().getLanguages())
                .withHealthCode(session.getUser().getHealthCode())
                .withStudyIdentifier(study.getStudyIdentifier())
                .withUserDataGroups(session.getUser().getDataGroups())
                .build();            
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
    
    public Builder getBuilder(Class<?> cls) {
        checkNotNull(cls);
        return new Builder(cls);
    }

    public void deleteUser(TestUser testUser) {
        checkNotNull(testUser);
        
        if (testUser.getSession() != null) {
            authService.signOut(testUser.getSession());
        }
        deleteUser(testUser.getStudy(), testUser.getId());
    }

    public void deleteUser(Study study, String id) {
        checkNotNull(study);
        checkNotNull(id);

        userAdminService.deleteUser(study, id);
    }
    
    public class Builder {
        private Class<?> cls;
        private Study study;
        private SubpopulationGuid subpopGuid;
        private boolean signIn;
        private boolean consent;
        private Set<Roles> roles;
        private Set<String> dataGroups;
        private SignUp signUp;
        
        private Builder(Class<?> cls) {
            this.cls = cls;
            this.signIn = true;
            this.consent = true;
        }
        public Builder withStudy(Study study) {
            this.study = study;
            return this;
        }
        public Builder withSignIn(boolean signIn) {
            this.signIn = signIn;
            return this;
        }
        public Builder withGuid(SubpopulationGuid subpopGuid) {
            this.subpopGuid = subpopGuid;
            return this;
        }
        public Builder withConsent(boolean consent) {
            this.consent = consent;
            return this;
        }
        public Builder withRoles(Roles... roles) {
            this.roles = Sets.newHashSet(roles);
            return this;
        }
        public Builder withDataGroups(Set<String> dataGroups) {
            this.dataGroups = dataGroups;
            return this;
        }
        public Builder withSignUp(SignUp signUp) {
            this.signUp = signUp;
            return this;
        }
        public TestUser build() {
            if (study == null) {
                study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
            }
            String email = TestUtils.makeRandomTestEmail(cls);
            SignUp finalSignUp = (signUp != null) ? signUp : new SignUp(email, PASSWORD, roles, dataGroups);
            UserSession session = userAdminService.createUser(finalSignUp, study, subpopGuid, signIn, consent);
            
            return new TestUser(finalSignUp, study, session);
        }
    }

}
