package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
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
    private SignIn FAILED_ADMIN_USER;
    private SignUp FAILED_USER;
    private SignUp USER;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        ADMIN_USER = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        FAILED_ADMIN_USER = new SignIn("test2", "P4ssword");
        FAILED_USER = new SignUp("user", "user@sagebase.org", "P4ssword");
        USER = new SignUp("user2", "user2@sagebase.org", "P4ssword");
    }
    
    @Test
    public void nonAdminUserCannotCreateUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, FAILED_ADMIN_USER);
            service.createAndSignInUser(adminSession.getSessionToken(), TestConstants.STUDY, FAILED_USER, true);
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    @Test
    public void nonAdminUserCannotConsentUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, FAILED_ADMIN_USER);
            service.revokeAllConsentRecords(adminSession.getSessionToken(), TestConstants.STUDY, "aaa",
                    "http://bogus.url/");
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    
    @Test
    public void nonAdminUserCannotDeleteUser() {
        try {
            UserSession adminSession = authService.signIn(TestConstants.STUDY, FAILED_ADMIN_USER);
            service.deleteUser(adminSession.getSessionToken(), "http://bogus.url");
            fail("Did not throw 403 exception");
        } catch(BridgeServiceException e) {
            assertEquals("Throws Forbidden (403) exception", HttpStatus.SC_FORBIDDEN, e.getStatusCode());
        }
    }
    
    @Test
    public void canCreateUserIdempotently() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        service.createAndSignInUser(adminSession.getSessionToken(), TestConstants.SECOND_STUDY, USER, true);
        UserSession newUserSession = service.createAndSignInUser(adminSession.getSessionToken(),
                TestConstants.SECOND_STUDY, USER, true);
        
        assertEquals("Correct email", USER.getEmail(), newUserSession.getUser().getEmail());
        assertEquals("Correct username", USER.getUsername(), newUserSession.getUser().getUsername());
        assertTrue("Has consented", newUserSession.doesConsent());
        
        authService.signOut(newUserSession.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), newUserSession.getUser().getStormpathHref());
    }
    
    @Test
    public void createdUserIsInCache() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        UserSession session1 = service.createAndSignInUser(adminSession.getSessionToken(),
                TestConstants.SECOND_STUDY, USER, true);
        
        UserSession session2 = authService.getSession(session1.getSessionToken());
        assertEquals("Session exists", session1.getSessionToken(), session2.getSessionToken());
        
        authService.signOut(session1.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), session1.getUser().getStormpathHref());
    }
    
    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        UserSession adminSession = authService.signIn(TestConstants.STUDY, ADMIN_USER);
        
        UserSession session1 = service.createAndSignInUser(adminSession.getSessionToken(),
                TestConstants.SECOND_STUDY, USER, true);
        
        authService.signOut(session1.getSessionToken());
        service.deleteUser(adminSession.getSessionToken(), session1.getUser().getStormpathHref());
        
        // This should fail with a 404.
        authService.signIn(TestConstants.STUDY, new SignIn(USER.getEmail(), USER.getPassword()));
    }

}
