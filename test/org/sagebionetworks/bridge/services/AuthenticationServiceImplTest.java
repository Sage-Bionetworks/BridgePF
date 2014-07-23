package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

import java.util.Iterator;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

import controllers.StudyControllerService;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    AuthenticationServiceImpl service;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    Client stormpathClient;
    
    @Resource
    BridgeConfig bridgeConfig;

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        service.signIn(TEST_STUDY, new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        service.signIn(TEST_STUDY, new SignIn("foo", null));
    }

    @Test(expected = BridgeNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        service.signIn(TEST_STUDY, new SignIn("foo", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = service.signIn(TEST_STUDY, new SignIn(TEST2.USERNAME, TEST2.PASSWORD));

        assertEquals("Username is for test2 user", TEST2.USERNAME, session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        service.signIn(TEST_STUDY, new SignIn(TEST2.USERNAME, TEST2.PASSWORD));

        UserSession session = service.signIn(TEST_STUDY, new SignIn(TEST2.USERNAME, TEST2.PASSWORD));
        assertEquals("Username is for test2 user", TEST2.USERNAME, session.getUser().getUsername());
    }

    @Test
    public void getSessionWhenNotAuthenticated() throws Exception {
        service.signOut(null); // This also tests sign out.
        UserSession session = service.getSession("foo");
        assertNull("Session is null", session);

        session = service.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession session = service.signIn(TEST_STUDY, new SignIn(TEST2.USERNAME, TEST2.PASSWORD));
        session = service.getSession(session.getSessionToken());

        assertEquals("Username is for test2 user", TEST2.USERNAME, session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        service.requestResetPassword(null);
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email("");
        service.requestResetPassword(email);
    }

    @Ignore
    @Test
    // This no longer works since we've moved to Storm Path
    public void canResetPassword() throws Exception {
        service.signIn(TEST_STUDY, new SignIn(TEST3.USERNAME, TEST3.PASSWORD));
        service.resetPassword(new PasswordReset("asdf", "newpassword"));
        service.signIn(TEST_STUDY, new SignIn(TEST3.USERNAME, "newpassword"));
        service.resetPassword(new PasswordReset("asdf", TEST2.PASSWORD));
    }

    @Test(expected = BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        service.signIn(TEST_STUDY, new SignIn(TEST2.USERNAME, TEST2.PASSWORD));
        service.resetPassword(new PasswordReset("foo", "newpassword"));
    }

    @Test(expected = ConsentRequiredException.class)
    public void unconsentedUserMustSignTOU() throws Exception {
        service.signIn(TEST_STUDY, new SignIn(TEST4.USERNAME, TEST4.PASSWORD));
    }
    
    @Test
    public void createUserInNonDefaultAccountStore() {
        Study study = new Study("Second Study", "secondstudy", "https://api.stormpath.com/v1/directories/5RfWcEwOK0l7goGe4ZX9cz", null, null);
        Study defaultStudy = studyControllerService.getStudies().iterator().next();
        SignUp signUp = new SignUp("secondStudyUser", "secondStudyUser@sagebase.org", "P4ssword");

        deleteAccount(signUp);
        service.signUp(signUp, study);

        // Should have been saved to this account store, not the default account store.
        Directory directory = stormpathClient.getResource(study.getStormpathDirectoryHref(), Directory.class);
        assertTrue("Account is in store", isInStore(directory, signUp));
        
        directory = stormpathClient.getResource(defaultStudy.getStormpathDirectoryHref(), Directory.class);
        assertFalse("Account is not in store", isInStore(directory, signUp));
    }

    private void deleteAccount(SignUp signUp) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(signUp.getEmail()));
        AccountList accounts = app.getAccounts(criteria);
        for (Account account : accounts) {
            account.delete();
        }
    }
    
    private boolean isInStore(Directory directory, SignUp signUp) {
        for (Account account : directory.getAccounts()) {
            if (account.getEmail().equals(signUp.getEmail())) {
                return true;
            }
        }
        return false;
    }
}
