package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

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

    private boolean areUsersEqual(User a, User b) {
        return (a.getEmail().equals(b.getEmail()) && a.getUsername().equals(b.getUsername()));
    }
    
    @Test
    public void canUpdateUserProfile() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                
            }
        });
    }

    @Test
    @Ignore
    // This is no longer true unless you are calling through a controller. May rewrite or delete this test not sure.
    // Also, you do not need to use the whole integration test infrastructure, which is slow, if you are not testing
    // the controllers.
    public void updateUserUpdatesUserSession() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                TestUser a = new TestUser("auser", "a@sagebase.org", "P4ssword");
                TestUser b = new TestUser("buser", "b@sagebase.org", "P4ssword");

                TestUtils.addUserToSession(a, session, stormpathClient);
                
                User user = a.getUser(null);
                UserProfile profile = b.getUserProfile(null);

                service.updateProfile(user, profile);
                // TODO
                // If you set this here, are we testing that it was set by updateUser()?
                session.setUser(user);

                // If we set the session in the cache, do are we verifying that this was done by the service?
                String sessionToken = session.getSessionToken();
                cache.setUserSession(sessionToken, session);

                assertTrue("User has been updated.", areUsersEqual(user, cache.getUserSession(sessionToken).getUser()));
            }
        });
    }

}
