package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;

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

    private boolean areUsersEqual(UserProfile a, UserProfile b) {
        if (a.getEmail().equals(b.getEmail()) && a.getUsername().equals(b.getUsername()))
            return true;

        return false;
    }

    @Test(expected = BridgeServiceException.class)
    public void createUserFromAccountWithInvalidAccountFails() {
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

                TestUtils.addUserToSession(a, session, stormpathClient);
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
