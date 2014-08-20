package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import controllers.StudyControllerService;

@ContextConfiguration("file:conf/application-context.xml")
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
    StudyControllerService studyControllerService;
    
    @Resource
    StormPathUserAdminService userAdminService;
    private Study study;
    
    private TestUser admin;
    private TestUser test2 = new TestUser("testUser2", "test2@sagebridge.org", "P4ssword");
    private TestUser test3 = new TestUser("testUser3", "test3@sagebridge.org", "P4ssword");
    private TestUser failedUser = new TestUser("failedUser", "failedUser@sagebridge.org", "P4ssword");
    
    private User adminUser;
    private User test2User;
    private User test3User;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
        
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        
        adminUser = authService.signIn(study, admin.getSignIn()).getUser();
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
        
        if (test2User != null) {
            userAdminService.deleteUser(adminUser, test2User);
            test2User = null;
        }
        if (test3User != null) {
            userAdminService.deleteUser(adminUser, test3User);
            test3User = null;
        }
    }

    @Test
    public void nonAdminUserCannotCreateUser() {
        try {
            userAdminService.createUser(adminUser, test2.getSignUp(), study, false, true);
            test2User = authService.signIn(study, test2.getSignIn()).getUser();
            
            service.createUser(test2User, failedUser.getSignUp(), study, true, true);
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void nonAdminUserCannotConsentUser() {
        try {
            userAdminService.createUser(adminUser, test2.getSignUp(), study, false, true);
            userAdminService.createUser(adminUser, test3.getSignUp(), study, false, true);
            
            test2User = authService.signIn(study, test2.getSignIn()).getUser();
            test3User = authService.signIn(study, test3.getSignIn()).getUser();
            service.revokeAllConsentRecords(test2User, test3User, TestConstants.STUDY);
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void nonAdminUserCannotDeleteUser() {
        try {
            userAdminService.createUser(adminUser, test2.getSignUp(), study, false, true);
            userAdminService.createUser(adminUser, test3.getSignUp(), study, false, true);
            
            test2User = authService.signIn(study, test2.getSignIn()).getUser();
            test3User = authService.signIn(study, test3.getSignIn()).getUser();
            service.deleteUser(test3User, test2User);
            
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void canCreateUserIdempotently() {
        test2User = service.createUser(adminUser, test2.getSignUp(), study, true, true).getUser();
        test2User = service.createUser(adminUser, test2.getSignUp(), study, true, true).getUser();

        assertEquals("Correct email", test2.getSignUp().getEmail(), test2User.getEmail());
        assertEquals("Correct username", test2.getSignUp().getUsername(), test2User.getUsername());
        assertTrue("Has consented", test2User.doesConsent());
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        test2User = service.createUser(adminUser, test2.getSignUp(), study, true, true).getUser();
        
        service.deleteUser(adminUser, test2User);
        
        // This should fail with a 404.
        authService.signIn(study, test2.getSignIn());
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = service.createUser(adminUser, test2.getSignUp(), study, false, false);
        assertNull("No session", session1);
        
        try {
            authService.signIn(study, test2.getSignIn());
            fail("Should throw a consent required exception");
        } catch (ConsentRequiredException e) {
            test2User = e.getUserSession().getUser();
        }
    }
}
