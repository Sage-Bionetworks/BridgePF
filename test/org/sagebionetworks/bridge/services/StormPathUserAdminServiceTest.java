package org.sagebionetworks.bridge.services;

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
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormPathUserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are
    // being tested here.

    @Resource
    AuthenticationServiceImpl authService;

    @Resource
    UserAdminServiceImpl service;

    @Resource
    BridgeConfig bridgeConfig;

    @Resource
    StudyServiceImpl studyService;

    @Resource
    UserAdminServiceImpl userAdminService;

    private Study study;

    private SignUp signUp;

    private User testUser;

    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        String name = bridgeConfig.getUser() + "-admin-" + RandomStringUtils.randomAlphabetic(4);
        signUp = new SignUp(name, name+"@sagebridge.org", "P4ssword", null);

        SignIn signIn = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        authService.signIn(study, signIn).getUser();
    }

    @After
    public void after() {
        if (testUser != null) {
            userAdminService.deleteUser(testUser.getEmail());
        }
    }

    @Test(expected = InvalidEntityException.class)
    public void cannotCreateUserIdempotently() {
        testUser = service.createUser(signUp, study, true, true).getUser();
        testUser = service.createUser(signUp, study, true, true).getUser();
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        testUser = service.createUser(signUp, study, true, true).getUser();

        service.deleteUser(testUser.getEmail());

        // This should fail with a 404.
        authService.signIn(study, new SignIn(signUp.getEmail(), signUp.getPassword()));
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = service.createUser(signUp, study, false, false);
        assertNull("No session", session1);

        try {
            authService.signIn(study, new SignIn(signUp.getEmail(), signUp.getPassword()));
            fail("Should throw a consent required exception");
        } catch (ConsentRequiredException e) {
            testUser = e.getUserSession().getUser();
        }
    }
}
