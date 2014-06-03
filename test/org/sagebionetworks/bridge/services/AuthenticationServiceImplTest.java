package org.sagebionetworks.bridge.services;

import static org.fest.assertions.Assertions.*;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    private static final String TEST2_USERNAME = "test2";

    private static final String PASSWORD = "P4ssword";

    @Resource
    AuthenticationServiceImpl service;
    
    Study study;
    
    @Before
    public void setupStudy() {
        study = new Study();
        study.setKey("test");
        study.setName("Test Study");
    }
    
    @Test(expected=BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        service.signIn(study, null, "bar");
    }
    
    @Test(expected=BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        service.signIn(study, "foo", null);
    }
    
    @Test(expected=BridgeNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        service.signIn(study, "foo", "bar");
    }
    
    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = service.signIn(study, TEST2_USERNAME, PASSWORD);
        assertThat(session.getUsername()).isEqualTo(TEST2_USERNAME);
        assertThat(session.getEnvironment()).isEqualTo("local");
        assertThat(session.getSessionToken()).isNotEmpty();
    }
    
    @Test
    public void signInWhenSignedIn() throws Exception {
        service.signIn(study, TEST2_USERNAME, PASSWORD);
        
        UserSession session = service.signIn(study, TEST2_USERNAME, PASSWORD);
        assertThat(session.getUsername()).isEqualTo(TEST2_USERNAME);
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
        UserSession session = service.signIn(study, TEST2_USERNAME, PASSWORD);
        session = service.getSession(session.getSessionToken());
        assertThat(session.getUsername()).isEqualTo(TEST2_USERNAME);
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
    
    @Test
    public void canResetPassword() throws Exception {
        service.signIn(study, "test3", PASSWORD);
        service.resetPassword("asdf", "newpassword");
        
        service.signIn(study, "test3", "newpassword");
        
        service.resetPassword("asdf", PASSWORD);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        service.signIn(study, TEST2_USERNAME, PASSWORD);
        service.resetPassword("foo", "newpassword");
    }
    
    @Test(expected=ConsentRequiredException.class)
    public void unconsentedUserMustSignTOU() throws Exception {
        service.signIn(study, "test4", PASSWORD);
    }
}
