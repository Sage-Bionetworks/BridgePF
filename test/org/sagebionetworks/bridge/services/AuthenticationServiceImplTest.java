package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    private CacheProvider cacheProvider;

    @Resource
    private AuthenticationServiceImpl authService;

    @Resource
    private ParticipantOptionsService optionsService;
    
    @Resource
    private AccountDao accountDao;
    
    @Resource
    private HealthCodeService healthCodeService;
    
    @Resource
    private StudyServiceImpl studyService;

    @Resource
    private TestUserAdminHelper helper;

    private TestUser testUser;

    @Before
    public void before() {
        testUser = helper.createUser(AuthenticationServiceImplTest.class);
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        if (study.getDataGroups().isEmpty()) {
            study.getDataGroups().add("foo");
            studyService.updateStudy(study, true);
        }
    }

    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        authService.signIn(testUser.getStudy(), new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(testUser.getStudy(), new SignIn("foo", null));
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(testUser.getStudy(), new SignIn("foo", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession newSession = authService.getSession(testUser.getSessionToken());
        assertEquals("Username is for test2 user", newSession.getUser().getUsername(), testUser.getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(testUser.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        String sessionToken = testUser.getSessionToken();
        UserSession newSession = authService.signIn(testUser.getStudy(), testUser.getSignIn());
        assertEquals("Username is for test2 user", testUser.getUsername(), newSession.getUser().getUsername());
        assertEquals("Should update the existing session instead of creating a new one.",
                sessionToken, newSession.getSessionToken());
    }

    @Test
    public void signInSetsSharingScope() { 
        UserSession newSession = authService.signIn(testUser.getStudy(), testUser.getSignIn());
        assertEquals(SharingScope.NO_SHARING, newSession.getUser().getSharingScope()); // this is the default.
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("foo");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession newSession = authService.getSession(testUser.getSessionToken());

        assertEquals("Username is for test2 user", testUser.getUsername(), newSession.getUser().getUsername());
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
        authService.resetPassword(new PasswordReset("newpassword", "foo"));
    }

    @Test
    public void unconsentedUserMustSignTOU() throws Exception {
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false);
        try {
            // Create a user who has not consented.
            authService.signIn(user.getStudy(), user.getSignIn());
            fail("Should have thrown consent exception");
        } catch (ConsentRequiredException e) {
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void canResendEmailVerification() throws Exception {
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false);
        try {
            Email email = new Email(testUser.getStudyIdentifier(), user.getEmail());
            authService.resendEmailVerification(user.getStudyIdentifier(), email);
        } finally {
            helper.deleteUser(user);
        }
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        TestUser researcher = helper.createUser(AuthenticationServiceImplTest.class, false, false,
                Sets.newHashSet(RESEARCHER), null);
        try {
            authService.signIn(researcher.getStudy(), researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        TestUser researcher = helper.createUser(AuthenticationServiceImplTest.class, false, false,
                        Sets.newHashSet(ADMIN), null);
        try {
            authService.signIn(researcher.getStudy(), researcher.getSignIn());
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
        
        Study tempStudy = TestUtils.getValidStudy(AuthenticationServiceImplTest.class);
        tempStudy.setDataGroups(Sets.newHashSet("group1","group2","group3"));
        tempStudy = studyService.createStudy(tempStudy);

        Set<String> list = Sets.newHashSet("group1");
        String name = TestUtils.randomName(AuthenticationServiceImplTest.class);
        String email = "bridge-testing+"+name+"@sagebase.org";
        
        try {
            SignUp signUp = new SignUp(name, email, "P@ssword1", null, list);

            authService.signUp(tempStudy, signUp, true);
            Account account = accountDao.getAccount(tempStudy, email);
            HealthId healthId = healthCodeService.getMapping(account.getHealthId());
            
            verify(authService).signUp(tempStudy, signUp, true);
            verify(optionsService).setDataGroups(tempStudy, healthId.getCode(), new DataGroups(list));
        } finally {
            accountDao.deleteAccount(tempStudy, email);
            studyService.deleteStudy(tempStudy.getIdentifier());
        }
    }
    
    @Test
    public void userCreatedWithDataGroupsHasThemOnSignIn() throws Exception {
        // @Before method always ensures there is one data group in the study. Use that for this test.
        String group = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER).getDataGroups().iterator().next();
        Set<String> dataGroups = Sets.newHashSet(group);
        
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, true, null, dataGroups);
        try {
            UserSession session = authService.signIn(user.getStudy(), user.getSignIn());
            // Verify we created a list and the anticipated group was not null
            assertEquals(1, session.getUser().getDataGroups().size()); 
            assertEquals(dataGroups, session.getUser().getDataGroups());
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void invalidDataGroupsAreRejected() throws Exception {
        try {
            Set<String> dataGroups = Sets.newHashSet("bugleboy");
            helper.createUser(AuthenticationServiceImplTest.class, false, false, null, dataGroups);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("dataGroups 'bugleboy' is not one of these valid values: foo."));
        }
    }
    
    // Account enumeration security. Verify the service is quite (throws no exceptions) when we don't
    // recognize an account.

    @Test
    public void secondSignUpTriggersResetPasswordInstead() {
        // Verify that requestResetPassword is called in this case
        authService = spy(authService);
        
        Study tempStudy = TestUtils.getValidStudy(AuthenticationServiceImplTest.class);
        tempStudy = studyService.createStudy(tempStudy);

        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false);
        try {
            authService.signUp(user.getStudy(), user.getSignUp(), true);
            authService.signUp(tempStudy, user.getSignUp(), true);

            verify(authService).requestResetPassword(any(Study.class), any(Email.class));
        } finally {
            studyService.deleteStudy(tempStudy.getIdentifier());    
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void resendEmailVerificationLooksSuccessfulWhenNoAccount() throws Exception {
        // In particular, it must not throw an EntityNotFoundException
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false);
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
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false);
        try {
            Email email = new Email(testUser.getStudyIdentifier(), "notarealaccount@sagebase.org");
            authService.requestResetPassword(testUser.getStudy(), email);
        } finally {
            helper.deleteUser(user);
        }
    }
    
}
