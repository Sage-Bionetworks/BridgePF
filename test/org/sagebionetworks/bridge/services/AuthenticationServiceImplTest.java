package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
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
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    private AuthenticationServiceImpl authService;
    
    @Resource
    private BridgeEncryptor healthCodeEncryptor;

    @Resource
    private HealthCodeService healthCodeService;

    @Resource
    private StudyServiceImpl studyService;
    
    @Resource
    private TestUserAdminHelper helper;

    @Resource
    private Client stormpathClient;
    
    private UserSession session;
    
    @Before
    public void before() {
        session = helper.createUser("test");
    }
    
    @After
    public void after() {
        helper.deleteUser(session);
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        authService.signIn(helper.getTestStudy(), new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(helper.getTestStudy(), new SignIn("foo", null));
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(helper.getTestStudy(), new SignIn("foo", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession newSession = authService.getSession(session.getSessionToken());

        assertEquals("Username is for test2 user", newSession.getUser().getUsername(), session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        UserSession newSession = authService.signIn(helper.getTestStudy(), helper.getSignIn(session));
        assertEquals("Username is for test2 user", session.getUser().getUsername(), newSession.getUser().getUsername());
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("foo");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession newSession = authService.getSession(session.getSessionToken());

        assertEquals("Username is for test2 user", session.getUser().getUsername(), newSession.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(newSession.getSessionToken()));
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
        authService.resetPassword(new PasswordReset("foo", "newpassword"));
    }

    @Test
    public void unconsentedUserMustSignTOU() throws Exception {
        UserSession aSession = null;
        try {
            // Create a user who has not consented.
            TestUser user = new TestUser("authTestUser", "authTestUser@sagebridge.org", "P4ssword");
            aSession = helper.createUser(user.getSignUp(), helper.getTestStudy(), false, false);
            authService.signIn(helper.getTestStudy(), user.getSignIn());
            fail("Should have thrown consent exception");
        } catch(ConsentRequiredException e) {
            helper.deleteUser(aSession);
        }
    }
    
    @Test
    public void createUserInNonDefaultAccountStore() {
        TestUser nonDefaultUser = new TestUser("secondStudyUser", "secondStudyUser@sagebridge.org", "P4ssword");
        try {
             
            Study defaultStudy = helper.getTestStudy();
            Study otherStudy = studyService.getStudyByKey("neurod");
            authService.signUp(nonDefaultUser.getSignUp(), otherStudy, false);

            // Should have been saved to this account store, not the default account store.
            Directory directory = stormpathClient.getResource(otherStudy.getStormpathDirectoryHref(), Directory.class);
            assertTrue("Account is in store", isInStore(directory, nonDefaultUser.getSignUp()));
            assertTrue("Account has health code", hasHealthCode(otherStudy, directory, nonDefaultUser.getSignUp()));
            directory = stormpathClient.getResource(defaultStudy.getStormpathDirectoryHref(), Directory.class);
            assertFalse("Account is not in store", isInStore(directory, nonDefaultUser.getSignUp()));
        } finally {
            deleteAccount(nonDefaultUser);
        }
    }
    
    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        UserSession session = null;
        try {
            TestUser researcher = new TestUser("researcher", "researcher@sagebridge.org", "P4ssword", helper
                    .getTestStudy().getResearcherRole());
            
            helper.createUser(researcher.getSignUp(), helper.getTestStudy(), false, false);
            
            session = authService.signIn(helper.getTestStudy(), researcher.getSignIn());
            // no exception should have been thrown.
            
        } finally {
            helper.deleteUser(session);
        }
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        UserSession session = null;
        try {
            TestUser researcher = new TestUser("adminer", "adminer@sagebridge.org", "P4ssword", BridgeConstants.ADMIN_GROUP);

            helper.createUser(researcher.getSignUp(), helper.getTestStudy(), false, false);
            
            session = authService.signIn(helper.getTestStudy(), researcher.getSignIn());
            // no exception should have been thrown.
            
        } finally {
            helper.deleteUser(session);
        }
    }
    
    private void deleteAccount(TestUser user) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(user.getEmail()));
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

    private boolean hasHealthCode(Study study, Directory directory, SignUp signUp) {
        for (Account account : directory.getAccounts()) {
            if (account.getEmail().equals(signUp.getEmail())) {
                return hasHealthCode(study, account);
            }
        }
        return false;
    }

    private boolean hasHealthCode(Study study, Account account) {
        final CustomData customData = account.getCustomData();
        final String hdcKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        final String encryptedId = (String)customData.get(hdcKey);
        if (encryptedId == null) {
            return false;
        }
        String healthId = healthCodeEncryptor.decrypt(encryptedId);
        if (healthId == null) {
            return false;
        }
        String healthCode = healthCodeService.getHealthCode(healthId);
        if (healthCode == null) {
            return false;
        }
        return true;
    }
}
