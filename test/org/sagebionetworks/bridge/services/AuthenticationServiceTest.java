package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;

import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.DefaultStudyBootstrapper;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceTest {

    @Resource
    private CacheProvider cacheProvider;

    @Resource
    private AuthenticationService authService;

    @Resource
    private ParticipantOptionsService optionsService;
    
    @Resource
    private AccountDao accountDao;
    
    @Resource
    private HealthCodeService healthCodeService;
    
    @Resource
    private StudyService studyService;

    @Resource
    private TestUserAdminHelper helper;

    private TestUser testUser;

    @Before
    public void before() {
        testUser = helper.getBuilder(AuthenticationServiceTest.class).build();
    }

    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoEmail() throws Exception {
        authService.signIn(testUser.getStudy(), ClientInfo.UNKNOWN_CLIENT, new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(testUser.getStudy(), ClientInfo.UNKNOWN_CLIENT, new SignIn("foobar", null));
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(testUser.getStudy(), ClientInfo.UNKNOWN_CLIENT, new SignIn("foobar", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession newSession = authService.getSession(testUser.getSessionToken());
        assertEquals("Email is for test2 user", newSession.getUser().getEmail(), testUser.getEmail());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(testUser.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        String sessionToken = testUser.getSessionToken();
        UserSession newSession = authService.signIn(testUser.getStudy(), ClientInfo.UNKNOWN_CLIENT, testUser.getSignIn());
        assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getUser().getEmail());
        assertEquals("Should update the existing session instead of creating a new one.",
                sessionToken, newSession.getSessionToken());
    }

    @Test
    public void signInSetsSharingScope() { 
        UserSession newSession = authService.signIn(testUser.getStudy(), ClientInfo.UNKNOWN_CLIENT, testUser.getSignIn());
        assertEquals(SharingScope.NO_SHARING, newSession.getUser().getSharingScope()); // this is the default.
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("anytoken");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession newSession = authService.getSession(testUser.getSessionToken());

        assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getUser().getEmail());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(newSession.getSessionToken()));
    }

    @Test(expected = NullPointerException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        authService.requestResetPassword(testUser.getStudy(), null);
    }

    @Test(expected = InvalidEntityException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email(testUser.getStudyIdentifier(), "");
        authService.requestResetPassword(testUser.getStudy(), email);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        authService.resetPassword(new PasswordReset("newpassword", "resettoken"));
    }

    @Test
    public void canResendEmailVerification() throws Exception {
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            Email email = new Email(testUser.getStudyIdentifier(), user.getEmail());
            authService.resendEmailVerification(user.getStudyIdentifier(), email);
        } finally {
            helper.deleteUser(user);
        }
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        TestUser researcher = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.RESEARCHER).build();
        try {
            authService.signIn(researcher.getStudy(), ClientInfo.UNKNOWN_CLIENT, researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        TestUser researcher = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.ADMIN).build();
        try {
            authService.signIn(researcher.getStudy(), ClientInfo.UNKNOWN_CLIENT, researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void testSignOut() {
        final String sessionToken = testUser.getSessionToken();
        final String userId = testUser.getUser().getId();
        authService.signOut(testUser.getSession());
        assertNull(cacheProvider.getUserSession(sessionToken));
        assertNull(cacheProvider.getUserSessionByUserId(userId));
    }

    @Test
    public void testSignOutWhenSignedOut() {
        final String sessionToken = testUser.getSessionToken();
        final String userId = testUser.getUser().getId();
        authService.signOut(testUser.getSession());
        authService.signOut(testUser.getSession());
        assertNull(cacheProvider.getUserSession(sessionToken));
        assertNull(cacheProvider.getUserSessionByUserId(userId));
    }
    
    @Test
    public void signUpWillCreateDataGroups() {
        authService = spy(authService);
        optionsService = spy(optionsService);
        authService.setOptionsService(optionsService);
        
        Study study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        
        Set<String> list = Sets.newHashSet("group1");
        String name = TestUtils.randomName(AuthenticationServiceTest.class);
        String email = "bridge-testing+"+name+"@sagebase.org";
        
        try {
            SignUp signUp = new SignUp(email, "P@ssword1", null, list);

            authService.signUp(study, signUp, true);
            Account account = accountDao.getAccount(study, email);
            HealthId healthId = healthCodeService.getMapping(account.getHealthId());
            
            verify(authService).signUp(study, signUp, true);
            // Verify that data groups were set correctly as an option
            Set<String> persistedGroups = optionsService.getStringSet(healthId.getCode(), DATA_GROUPS);
            assertEquals(list, persistedGroups);
            
        } finally {
            accountDao.deleteAccount(study, email);
        }
    }
    
    @Test
    public void userCreatedWithDataGroupsHasThemOnSignIn() throws Exception {
        int numOfGroups = DefaultStudyBootstrapper.TEST_DATA_GROUPS.size();
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class).withConsent(true)
                .withDataGroups(DefaultStudyBootstrapper.TEST_DATA_GROUPS).build();
        try {
            UserSession session = authService.signIn(user.getStudy(), ClientInfo.UNKNOWN_CLIENT, user.getSignIn());
            // Verify we created a list and the anticipated group was not null
            assertEquals(numOfGroups, session.getUser().getDataGroups().size()); 
            assertEquals(DefaultStudyBootstrapper.TEST_DATA_GROUPS, session.getUser().getDataGroups());
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void invalidDataGroupsAreRejected() throws Exception {
        try {
            Set<String> dataGroups = Sets.newHashSet("bugleboy");
            helper.getBuilder(AuthenticationServiceTest.class).withConsent(false).withSignIn(false)
                    .withDataGroups(dataGroups).build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("dataGroups 'bugleboy' is not one of these valid values"));
        }
    }
    
    // Account enumeration security. Verify the service is quite (throws no exceptions) when we don't
    // recognize an account.

    @Test
    public void secondSignUpTriggersResetPasswordInstead() {
        // Verify that requestResetPassword is called in this case
        authService = spy(authService);
        
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            authService.signUp(user.getStudy(), user.getSignUp(), true);
            verify(authService).requestResetPassword(any(Study.class), any(Email.class));
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void resendEmailVerificationLooksSuccessfulWhenNoAccount() throws Exception {
        // In particular, it must not throw an EntityNotFoundException
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            Email email = new Email(testUser.getStudyIdentifier(), "notarealaccount@sagebase.org");
            authService.resendEmailVerification(user.getStudyIdentifier(), email);
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void requestResetPasswordLooksSuccessfulWhenNoAccount() throws Exception {
        // In particular, it must not throw an EntityNotFoundException
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            Email email = new Email(testUser.getStudyIdentifier(), "notarealaccount@sagebase.org");
            authService.requestResetPassword(testUser.getStudy(), email);
        } finally {
            helper.deleteUser(user);
        }
    }
    
    // Consent statuses passed on to sessionInfo
    
    @Test
    public void consentStatusesPresentInSession() {
        // User is consenting
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();
        try {
            SubpopulationGuid guid = SubpopulationGuid.create(testUser.getStudyIdentifier().getIdentifier());
            
            // This is the object we pass back to the user, we want to see the statuses copied or present
            // all the way from the user to the sessionInfo. We test elsewhere that these are properly 
            // serialized/deserialized (SubpopulationGuidDeserializer)
            UserSessionInfo sessionInfo = new UserSessionInfo(user.getSession());
            
            ConsentStatus status = sessionInfo.getConsentStatuses().get(guid);
            assertTrue(status.isConsented());
            assertEquals(testUser.getStudyIdentifier().getIdentifier(), status.getSubpopulationGuid());
        } finally {
            helper.deleteUser(user);
        }
    }
    
}
