package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
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
    private SignUp FAILED_USER;
    private SignUp USER;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        ADMIN_USER = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        TEST2_USER = new SignIn(TestConstants.TEST2.USERNAME, TestConstants.TEST2.PASSWORD);
        FAILED_USER = new SignUp("user", "user@sagebase.org", "P4ssword");
        USER = new SignUp("user2", "user2@sagebase.org", "P4ssword");
    }
    
    @Test
    @Ignore
    public void nonAdminUserCannotCreateUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.createUser(adminSession.getSessionToken(), TestConstants.STUDY, FAILED_USER, true, true);
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    @Test
    @Ignore
    public void nonAdminUserCannotConsentUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.revokeAllConsentRecords(adminSession.getSessionToken(), TestConstants.STUDY, "aaa");
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    
    @Test
    @Ignore
    public void nonAdminUserCannotDeleteUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, TEST2_USER);
            service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, TestConstants.TEST2.EMAIL);
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    
    @Test
    @Ignore
    public void canCreateUserIdempotently() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER, true, true);
        UserSession newUserSession = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY,
                USER, true, true);
        
        assertEquals("Correct email", USER.getEmail(), newUserSession.getUser().getEmail());
        assertEquals("Correct username", USER.getUsername(), newUserSession.getUser().getUsername());
        assertTrue("Has consented", newUserSession.doesConsent());
        
        authService.signOut(newUserSession.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, newUserSession.getUser().getEmail());
    }
    
    @Test
    @Ignore
    public void createdUserIsInCache() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
                true, true);
        
        UserSession session2 = authService.getSession(session1.getSessionToken());
        assertEquals("Session exists", session1.getSessionToken(), session2.getSessionToken());
        
        authService.signOut(session1.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, session1.getUser().getEmail());
    }
    
    @Test(expected = BridgeServiceException.class)
    @Ignore
    public void deletedUserHasBeenDeleted() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
                true, true);
        
        authService.signOut(session1.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, session1.getUser().getEmail());
        
        // This should fail with a 404.
        authService.signIn(TestConstants.STUDY, new SignIn(USER.getEmail(), USER.getPassword()));
    }

    @Test
    @Ignore
    public void canCreateUserWithoutSigningUserIn() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        UserSession session1 = service.createUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER,
                false, false);
        
        assertNull("No session", session1);
        
        try {
            authService.signIn(TestConstants.SECOND_STUDY, new SignIn(USER.getUsername(), USER.getPassword()));
        } catch(ConsentRequiredException e) {
            service.deleteUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER.getEmail());
        }
    }
    
}
