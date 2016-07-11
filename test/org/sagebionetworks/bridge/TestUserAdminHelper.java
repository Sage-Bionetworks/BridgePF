package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Set;

import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
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
        private final Study study;
        private final UserSession session;

        public TestUser(Study study, UserSession session) {
            this.study = study;
            this.session = session;
        }
        public StudyParticipant getStudyParticipant() {
            return session.getParticipant();
        }
        public SignIn getSignIn() {
            return new SignIn(getStudyParticipant().getEmail(), getStudyParticipant().getPassword());
        }
        public String getEmail() {
            return getStudyParticipant().getEmail();
        }
        public String getPassword() {
            return getStudyParticipant().getPassword();
        }
        public UserSession getSession() {
            return session;
        }
        public String getSessionToken() {
            return (session == null) ? null : session.getSessionToken();
        }
        public Study getStudy() {
            return study;
        }
        public String getHealthCode() {
            return (session == null) ? null : session.getHealthCode();
        }
        public String getId() {
            return (session == null) ? null : session.getId();
        }
        public StudyIdentifier getStudyIdentifier() {
            return study.getStudyIdentifier();
        }
        public CriteriaContext getCriteriaContext() {
            return new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withStudyIdentifier(study.getStudyIdentifier())
                .withUserDataGroups(session.getParticipant().getDataGroups())
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
        private String email;
        private String password;
        
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
        public Builder withRoles(Set<Roles> roles) {
            this.roles = Sets.newHashSet(roles);
            return this;
        }
        public Builder withRoles(Roles... roles) {
            this.roles = Sets.newHashSet(roles);
            this.roles.add(TEST_USERS);
            return this;
        }
        public Builder withDataGroups(Set<String> dataGroups) {
            this.dataGroups = dataGroups;
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }
        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }
        public TestUser build() {
            // There are tests where we partially create a user with the builder, then change just
            // some fields before creating another test user with multiple build() calls. Anything we
            // default in thie build method needs to be updated with each call to build().
            Study finalStudy = (study == null) ? studyService.getStudy(TEST_STUDY_IDENTIFIER) : study;
            String finalEmail = (email == null) ? TestUtils.makeRandomTestEmail(cls) : email;
            String finalPassword = (password == null) ? PASSWORD : password;
            StudyParticipant finalParticipant = new StudyParticipant.Builder().withEmail(finalEmail)
                    .withPassword(finalPassword).withRoles(roles).withDataGroups(dataGroups).build();
            
            UserSession session = userAdminService.createUser(
                    finalStudy, finalParticipant, subpopGuid, signIn, consent);

            // fix some stuff. In the admin/test path, we want to refer to the password after-the-fact.
            StudyParticipant newParticipant = new StudyParticipant.Builder()
                    .copyOf(session.getParticipant())
                    .withPassword(finalParticipant.getPassword())
                    .build();
            session.setParticipant(newParticipant);
            
            return new TestUser(finalStudy, session);
        }
    }

}
