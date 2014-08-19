package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormPathUserAdminServiceTest implements InitializingBean {

    @Resource
    AuthenticationServiceImpl authService;

    @Resource
    StormPathUserAdminService service;

    @Resource
    BridgeConfig bridgeConfig;

    private SignIn ADMIN_USER;
    private SignIn TEST2_USER;
    private SignIn TEST3_USER;
    private SignUp FAILED_USER;
    private SignUp USER;

    @Override
    public void afterPropertiesSet() throws Exception {
        ADMIN_USER = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        TEST2_USER = new SignIn(TestConstants.TEST2.USERNAME, TestConstants.TEST2.PASSWORD);
        TEST3_USER = new SignIn(TestConstants.TEST3.USERNAME, TestConstants.TEST3.PASSWORD);
        FAILED_USER = new SignUp("user", "user@sagebase.org", "P4ssword");
        USER = new SignUp("user2", "user2@sagebase.org", "P4ssword");
    }

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");

    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");

    }

    @Test
    public void nonAdminUserCannotCreateUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.createUser(adminSession.getSessionToken(), TestConstants.STUDY, FAILED_USER, true, true);
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void nonAdminUserCannotConsentUser() {
        try {
            UserSession session = authService.signIn(TestConstants.STUDY, TEST3_USER);
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.revokeAllConsentRecords(adminSession.getSessionToken(), session.getSessionToken(),
                    TestConstants.STUDY);
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void nonAdminUserCannotDeleteUser() {
        try {
            UserSession session = authService.signIn(TestConstants.STUDY, TEST3_USER);
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.deleteUser(adminSession.getSessionToken(), session.getSessionToken(), TestConstants.SECOND_STUDY);
            fail("Did not throw 403 exception");
        } catch (BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    public void canCreateUserIdempotently() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);

        service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER, true, true);
        UserSession newUserSession = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY,
                USER, true, true);

        assertEquals("Correct email", USER.getEmail(), newUserSession.getUser().getEmail());
        assertEquals("Correct username", USER.getUsername(), newUserSession.getUser().getUsername());
        assertTrue("Has consented", newUserSession.doesConsent());

        
        service.deleteUser(adminSession.getSessionToken(), newUserSession.getSessionToken(), TestConstants.SECOND_STUDY);
    }

    @Test
    public void createdUserIsInCache() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);

        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
                true, true);

        UserSession session2 = authService.getSession(session1.getSessionToken());
        assertEquals("Session exists", session1.getSessionToken(), session2.getSessionToken());

        service.deleteUser(adminSession.getSessionToken(), session1.getSessionToken(), TestConstants.SECOND_STUDY);
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);

        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
                true, true);

        
        service.deleteUser(adminSession.getSessionToken(), session1.getSessionToken(), TestConstants.SECOND_STUDY);
        
        // This should fail with a 404.
        authService.signIn(TestConstants.STUDY, new SignIn(USER.getEmail(), USER.getPassword()));
    }

    // Commenting out this test for now. This requires that signing in throws a
    // ConsentRequiredException, but we are required to sign in to delete a user.
//    @Test
//    public void canCreateUserWithoutSigningUserIn() {
//        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
//
//        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
//                false, false);
//
//        assertNull("No session", session1);
//
//        try {
//            authService.signIn(TestConstants.SECOND_STUDY, new SignIn(USER.getUsername(), USER.getPassword()));
//        } catch (ConsentRequiredException e) {
//            service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER.getEmail());
//        }
//    }
}
