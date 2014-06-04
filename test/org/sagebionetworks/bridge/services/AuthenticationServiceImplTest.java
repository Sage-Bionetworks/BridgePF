package org.sagebionetworks.bridge.services;

import static org.fest.assertions.Assertions.*;
import static org.sagebionetworks.bridge.TestConstants.*;

import javax.annotation.Resource;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    AuthenticationServiceImpl service;
    
    @Test(expected=BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        service.signIn(TEST_STUDY, null, "bar");
    }
    
    @Test(expected=BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        service.signIn(TEST_STUDY, "foo", null);
    }
    
    @Test(expected=BridgeNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        service.signIn(TEST_STUDY, "foo", "bar");
    }
    
    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = service.signIn(TEST_STUDY, TEST2.USERNAME, TEST2.PASSWORD);
        assertThat(session.getUsername()).isEqualTo(TEST2.USERNAME);
        assertThat(session.getEnvironment()).isEqualTo("local");
        assertThat(session.getSessionToken()).isNotEmpty();
    }
    
    @Test
    public void signInWhenSignedIn() throws Exception {
        service.signIn(TEST_STUDY, TEST2.USERNAME, TEST2.PASSWORD);
        
        UserSession session = service.signIn(TEST_STUDY, TEST2.USERNAME, TEST2.PASSWORD);
        assertThat(session.getUsername()).isEqualTo(TEST2.USERNAME);
    }

    @Test
    public void getSessionWhenNotAuthenticated() throws Exception {
        service.signOut(null); // This also tests sign out.
        UserSession session = service.getSession("foo");
        assertThat(session.getUsername()).isNull();
        assertThat(session.getSessionToken()).isNull();
        assertThat(session.getEnvironment()).isNull();
        
        session = service.getSession(null);
        assertThat(session.getUsername()).isNull();
        assertThat(session.getSessionToken()).isNull();
        assertThat(session.getEnvironment()).isNull();
    }
    
    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession session = service.signIn(TEST_STUDY, TEST2.USERNAME, TEST2.PASSWORD);
        session = service.getSession(session.getSessionToken());
        assertThat(session.getUsername()).isEqualTo(TEST2.USERNAME);
        assertThat(session.getEnvironment()).isEqualTo("local");
        assertThat(session.getSessionToken()).isNotEmpty();
    }
    
    @Test(expected=BridgeServiceException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        service.requestResetPassword(null);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        service.requestResetPassword("");
    }
    
    @Ignore
    @Test
    // This no longer works since we've moved to Storm Path
    public void canResetPassword() throws Exception {
        service.signIn(TEST_STUDY, TEST3.USERNAME, TEST3.PASSWORD);
        service.resetPassword("asdf", "newpassword");
        
        service.signIn(TEST_STUDY, TEST3.USERNAME, "newpassword");
        
        service.resetPassword("asdf", TEST2.PASSWORD);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        service.signIn(TEST_STUDY, TEST2.USERNAME, TEST2.PASSWORD);
        service.resetPassword("foo", "newpassword");
    }
    
    @Test(expected=ConsentRequiredException.class)
    public void unconsentedUserMustSignTOU() throws Exception {
        service.signIn(TEST_STUDY, TEST4.USERNAME, TEST4.PASSWORD);
    }
}
