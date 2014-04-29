package org.sagebionetworks.bridge.services;

import static org.fest.assertions.Assertions.*;

import javax.annotation.Resource;

import models.UserSession;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/stub-application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    AuthenticationServiceImpl service;
    
    @Test(expected=IllegalArgumentException.class)
    public void signInNoUsername() throws Exception {
        service.signIn(null, "bar");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void signInNoPassword() throws Exception {
        service.signIn("foo", null);
    }
    
    @Test(expected=SynapseNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        service.signIn("foo", "bar");
    }
    
    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = service.signIn("test2", "password");
        assertThat(session.getUsername()).isEqualTo("test2");
        assertThat(session.getEnvironment()).isEqualTo("stub");
        assertThat(session.getSessionToken()).isNotEmpty();
    }
    
    @Test
    public void signInWhenSignedIn() throws Exception {
        service.signIn("test2", "password");
        
        UserSession session = service.signIn("test2", "password");
        assertThat(session.getUsername()).isEqualTo("test2");
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
        UserSession session = service.signIn("test2", "password");
        session = service.getSession(session.getSessionToken());
        assertThat(session.getUsername()).isEqualTo("test2");
        assertThat(session.getEnvironment()).isEqualTo("stub");
        assertThat(session.getSessionToken()).isNotEmpty();
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        service.requestResetPassword(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        service.requestResetPassword("");
    }
    
    @Test
    public void canResetPassword() throws Exception {
        service.signIn("test3", "password");
        service.resetPassword("asdf", "newpassword");
        
        service.signIn("test3", "newpassword");
        
        service.resetPassword("asdf", "password");
    }
    
    @Test(expected=SynapseClientException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        service.signIn("test2", "password");
        service.resetPassword("foo", "newpassword");
    }
    
    @Test(expected=ConsentRequiredException.class)
    public void unconsentedUserMustSignTOU() throws Exception {
        service.signIn("test4", "password");
    }
}
