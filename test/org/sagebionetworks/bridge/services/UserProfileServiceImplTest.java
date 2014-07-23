package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.*;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountStore;
import com.stormpath.sdk.directory.Directory;

import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.*;

public class UserProfileServiceImplTest {
    private Client stormpathClient;
    private UserSession session;
    private CacheProvider cache;
    private UserProfileServiceImpl service;

    @Before
    public void before() {
        stormpathClient = StormpathFactory.createStormpathClient();

        session = new UserSession();

        cache = mock(CacheProvider.class);
        when(cache.getUserSession(anyString())).thenReturn(session);

        service = new UserProfileServiceImpl();
        service.setStormpathClient(stormpathClient);
    }

    private void addUserToSession(UserCredentials cred) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);

        // If account associated with email already exists, delete it.
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(cred.EMAIL));
        AccountList accounts = app.getAccounts(criteria);
        for (Account x : accounts) {
            x.delete();
        }

        // Create Account using credentials
        Account account = stormpathClient.instantiate(Account.class);
        account.setEmail(cred.EMAIL);
        account.setPassword(cred.PASSWORD);
        account.setGivenName(cred.FIRSTNAME);
        account.setSurname(cred.LASTNAME);
        AccountStore store = app.getDefaultAccountStore();
        Directory directory = stormpathClient.getResource(store.getHref(), Directory.class);
        directory.createAccount(account);

        // Create UserProfile using credentials and existing account's HREF
        UserProfile user = new UserProfile();
        user.setEmail(cred.EMAIL);
        user.setUsername(cred.USERNAME);
        user.setFirstName(cred.FIRSTNAME);
        user.setLastName(cred.LASTNAME);
        user.setStormpathHref(account.getHref());

        session.setUser(user);
    }

    private boolean areUsersEqual(UserProfile a, UserProfile b) {
        if (a.getEmail().equals(b.getEmail()) && a.getUsername().equals(b.getUsername()))
            return true;

        return false;
    }

    @Test(expected = BridgeServiceException.class)
    public void createUserFromAccountWithInvalidAccountFails500() {
        Account account = null;
        service.createUserFromAccount(account);
    }
    
    @Test
    public void updateUserUpdatesUserSession() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                UserCredentials a = new UserCredentials("auser", "P4ssword", "a@sagebase.org", "afirstname",
                        "alastname");
                UserCredentials b = new UserCredentials("buser", "P4ssword", "b@sagebase.org", "bfirstname",
                        "blastname");

                addUserToSession(a);
                UserProfile user = TestUtils.constructTestUser(b);

                service.updateUser(user, session.getUser());
                session.setUser(user);

                String sessionToken = session.getSessionToken();
                cache.setUserSession(sessionToken, session);

                assertTrue("User has been updated.", areUsersEqual(user, cache.getUserSession(sessionToken).getUser()));
            }
        });
    }

}
