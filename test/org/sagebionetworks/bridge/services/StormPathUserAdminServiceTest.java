package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_KEY;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
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
    StormPathUserAdminService service;

    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    StormPathUserAdminService userAdminService;
    
    private Study study;
    
    private TestUser test2 = new TestUser("testUser2", "test2@sagebridge.org", "P4ssword");
    
    private User test2User;
    private User test3User;
    
    private boolean setUpComplete = false;

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
        if (!setUpComplete) {
            study = studyService.getStudyByKey(TEST_STUDY_KEY);
            
            SignIn signIn = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
            authService.signIn(study, signIn).getUser();
            
            setUpComplete = true;
        }
    }

    @After
    public void after() {
        if (test2User != null) {
            userAdminService.deleteUser(test2User);
            test2User = null;
        }
        if (test3User != null) {
            userAdminService.deleteUser(test3User);
            test3User = null;
        }
    }

    @Test
    public void canCreateUserIdempotently() {
        test2User = service.createUser(test2.getSignUp(), null, study, true, true).getUser();
        test2User = service.createUser(test2.getSignUp(), null, study, true, true).getUser();

        assertEquals("Correct email", test2.getSignUp().getEmail(), test2User.getEmail());
        assertEquals("Correct username", test2.getSignUp().getUsername(), test2User.getUsername());
        assertTrue("Has consented", test2User.doesConsent());
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        test2User = service.createUser(test2.getSignUp(), null, study, true, true).getUser();
        
        service.deleteUser(test2User);
        
        // This should fail with a 404.
        authService.signIn(study, test2.getSignIn());
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = service.createUser(test2.getSignUp(), null, study, false, false);
        assertNull("No session", session1);
        
        try {
            authService.signIn(study, test2.getSignIn());
            fail("Should throw a consent required exception");
        } catch (ConsentRequiredException e) {
            test2User = e.getUserSession().getUser();
        }
    }
}
