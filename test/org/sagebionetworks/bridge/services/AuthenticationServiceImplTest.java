package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
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
    
    private static TestUser testUser = new TestUser("testUser", "tester@sagebridge.org", "P4ssword");
    private static TestUser testUser2 = new TestUser("test2User", "tester2@sagebridge.org", "P4ssword");
    private Study study;
    private User adminUser; 
    private User user;
    private User user2;
    
    @Resource
    AuthenticationServiceImpl authService;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    Client stormpathClient;
    
    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StormPathUserAdminService userAdminService;
    
    @Before
    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        TestUser admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminUser = authService.signIn(study, admin.getSignIn()).getUser();

        user = userAdminService.createUser(adminUser, testUser.getSignUp(), study, true, true).getUser();
        UserSession session = userAdminService.createUser(adminUser, testUser2.getSignUp(), study, true, false);
        if (session == null) {
            throw new RuntimeException("Yup, it's not returning a sessino");
        }
        user2 = session.getUser();
    }
    
    @After
    public void after() {
        if (user != null) {
            userAdminService.deleteUser(adminUser, user, study);    
        }
        if (user2 != null) {
            userAdminService.deleteUser(adminUser, user2, study);    
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        authService.signIn(study, new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(study, new SignIn("foo", null));
    }

    @Test(expected = BridgeNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(study, new SignIn("foo", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = authService.signIn(study, testUser.getSignIn());
        user = session.getUser();

        assertEquals("Username is for test2 user", testUser.getUsername(), session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        user = authService.signIn(study, testUser.getSignIn()).getUser();

        UserSession session = authService.signIn(study, testUser.getSignIn());
        assertEquals("Username is for test2 user", testUser.getUsername(), session.getUser().getUsername());
    }

    @Test
    public void getSessionWhenNotAuthenticated() throws Exception {
        authService.signOut(null); // This also tests sign out.
        UserSession session = authService.getSession("foo");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession session = authService.signIn(study, testUser.getSignIn());
        user = session.getUser();
        session = authService.getSession(session.getSessionToken());

        assertEquals("Username is for test2 user", testUser.getUsername(), session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        authService.requestResetPassword(null);
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email("");
        authService.requestResetPassword(email);
    }

    @Test(expected = BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        user = authService.signIn(study, testUser.getSignIn()).getUser();
        authService.resetPassword(new PasswordReset("foo", "newpassword"));
    }

    @Test(expected = ConsentRequiredException.class)
    public void unconsentedUserMustSignTOU() throws Exception {
        user2 = authService.signIn(study, testUser2.getSignIn()).getUser();
    }
    
    @Test
    public void createUserInNonDefaultAccountStore() {
        Study defaultStudy = studyControllerService.getStudies().iterator().next();
        
        TestUser nonDefaultUser = new TestUser("secondStudyUser", "secondStudyUser@sagebase.org", "P4ssword");

        deleteAccount(nonDefaultUser.getSignUp());
        authService.signUp(nonDefaultUser.getSignUp(), TestConstants.SECOND_STUDY);

        // Should have been saved to this account store, not the default account store.
        Directory directory = stormpathClient.getResource(TestConstants.SECOND_STUDY.getStormpathDirectoryHref(),
                Directory.class);
        assertTrue("Account is in store", isInStore(directory, nonDefaultUser.getSignUp()));
        
        directory = stormpathClient.getResource(defaultStudy.getStormpathDirectoryHref(), Directory.class);
        assertFalse("Account is not in store", isInStore(directory, nonDefaultUser.getSignUp()));
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
