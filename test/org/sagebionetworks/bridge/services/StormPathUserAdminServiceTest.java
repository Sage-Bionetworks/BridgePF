package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent3;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormPathUserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are
    // being tested here.

    @Resource
    AuthenticationService authService;

    @Resource
    BridgeConfig bridgeConfig;

    @Resource
    StudyService studyService;
    
    @Resource
    SubpopulationService subpopService;

    @Resource
    UserAdminService userAdminService;
    
    private Study study;

    private SignUp signUp;

    private User testUser;

    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent3.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent3.class);
    }

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        String name = bridgeConfig.getUser() + "-admin-" + RandomStringUtils.randomAlphabetic(4);
        signUp = new SignUp(name, name+"@sagebridge.org", "P4ssword!", null, null);

        SignIn signIn = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn).getUser();
    }

    @After
    public void after() {
        if (testUser != null) {
            userAdminService.deleteUser(study, testUser.getEmail());
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        testUser = userAdminService.createUser(signUp, study, null, true, true).getUser();

        userAdminService.deleteUser(study, testUser.getEmail());

        // This should fail with a 404.
        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, new SignIn(signUp.getEmail(), signUp.getPassword()));
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = userAdminService.createUser(signUp, study, null, false, false);
        assertNull("No session", session1);

        UserSession session = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, new SignIn(signUp.getEmail(),
                signUp.getPassword()));
        testUser = session.getUser();
        assertFalse(testUser.doesConsent());
    }

    // Next two test the same thing in two different ways.
    public void cannotCreateTheSameUserTwice() {
        testUser = userAdminService.createUser(signUp, study, null, true, true).getUser();
        testUser = userAdminService.createUser(signUp, study, null, true, true).getUser();
    }
    
    @Test
    public void cannotCreateUserWithSameUsernameOrEmail() {
        testUser = userAdminService.createUser(signUp, study, null, true, false).getUser();
        
        try {
            SignUp sameWithDifferentUsername = new SignUp(RandomStringUtils.randomAlphabetic(8), signUp.getEmail(), signUp.getPassword(), null, null);
            userAdminService.createUser(sameWithDifferentUsername, study, null, false, false);
            fail("Sign up with email already in use should throw an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("Account already exists.", e.getMessage());
        }
        try {
            String name = bridgeConfig.getUser() + "-admin-" + RandomStringUtils.randomAlphabetic(4);
            String email = name+"@sagebridge.org";
            SignUp sameWithDifferentEmail = new SignUp(signUp.getUsername(), email, signUp.getPassword(), null, null);
            userAdminService.createUser(sameWithDifferentEmail, study, null, false, false);
            fail("Sign up with username already in use should throw an exception");
        } catch(EntityAlreadyExistsException e) { 
            assertEquals("Account already exists.", e.getMessage());
        }
    }

    @Test
    public void testDeleteUserWhenSignedOut() {
        UserSession session = userAdminService.createUser(signUp, study, null, true, true);
        authService.signOut(session);
        assertNull(authService.getSession(session.getSessionToken()));
        // Shouldn't crash
        userAdminService.deleteUser(study, session.getUser().getEmail());
        assertNull(authService.getSession(session.getSessionToken()));
    }

    @Test
    public void testDeleteUserThatHasBeenDeleted() {
        UserSession session = userAdminService.createUser(signUp, study, null, true, true);
        userAdminService.deleteUser(study, session.getUser().getEmail());
        assertNull(authService.getSession(session.getSessionToken()));
        // Delete again shouldn't crash
        userAdminService.deleteUser(study, session.getUser().getEmail());
        assertNull(authService.getSession(session.getSessionToken()));
    }
    
}
