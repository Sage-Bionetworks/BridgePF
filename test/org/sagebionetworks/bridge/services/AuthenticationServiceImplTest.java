package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;
import com.stormpath.sdk.client.Client;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    private AuthenticationServiceImpl authService;

    @Resource
    private AccountEncryptionService accountEncryptionService;

    @Resource
    private StudyServiceImpl studyService;

    @Resource
    private TestUserAdminHelper helper;

    @Resource
    private Client stormpathClient;

    private TestUser testUser;

    @Before
    public void before() {
        testUser = helper.createUser(AuthenticationServiceImplTest.class);
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
        UserSession newSession = authService.signIn(testUser.getStudy(), testUser.getSignIn());
        assertEquals("Username is for test2 user", testUser.getUsername(), newSession.getUser().getUsername());
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
        authService.requestResetPassword(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email("");
        authService.requestResetPassword(email);
    }

    @Test(expected = BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        authService.resetPassword(new PasswordReset("foo", "newpassword"));
    }

    @Test
    public void unconsentedUserMustSignTOU() throws Exception {
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false, null);
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
        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false, null);
        try {
            Email email = new Email(user.getEmail());
            authService.resendEmailVerification(user.getStudy(), email);
        } catch (ConsentRequiredException e) {
        } finally {
            helper.deleteUser(user);
        }
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        Study study = studyService.getStudyByIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        TestUser researcher = helper.createUser(AuthenticationServiceImplTest.class, false, false,
                Sets.newHashSet(study.getResearcherRole()));
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
                Sets.newHashSet(BridgeConstants.ADMIN_GROUP));
        try {
            authService.signIn(researcher.getStudy(), researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void cannotCreateTheSameEmailAccountTwice() {
        // To really test this, you need to create another study, and then try and add the user to *that*.
        Study tempStudy = new DynamoStudy();
        tempStudy.setIdentifier("temp");
        tempStudy.setName("Temporary Study");
        tempStudy = studyService.createStudy(tempStudy);

        TestUser user = helper.createUser(AuthenticationServiceImplTest.class, false, false, null);
        try {
            authService.signUp(user.getSignUp(), user.getStudy(), false);
            authService.signUp(user.getSignUp(), tempStudy, false);
            fail("Should not get here");
        } catch (InvalidEntityException e) {
            String message = e.getErrors().get("email").get(0);
            assertEquals("email has already been registered", message);
        } finally {
            studyService.deleteStudy(tempStudy.getIdentifier());    
            helper.deleteUser(user);
        }
    }
}
