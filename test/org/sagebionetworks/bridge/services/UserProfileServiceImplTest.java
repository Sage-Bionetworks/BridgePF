package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.User;
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
import static play.test.Helpers.*;

public class UserProfileServiceImplTest {
	private Client stormpathClient;
	private UserProfileServiceImpl service;
	
	@Before
	public void before() {
	    stormpathClient = StormpathFactory.createStormpathClient();
	    	    
	    service = new UserProfileServiceImpl();
	    service.setStormpathClient(stormpathClient);
	}
	
	private UserSession constructUserSession(UserCredentials cred) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);
        
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(cred.EMAIL));
        AccountList accounts = app.getAccounts(criteria);
        for (Account x : accounts) x.delete();
        
        Account account = stormpathClient.instantiate(Account.class);
        account.setEmail(cred.EMAIL);
        account.setPassword(cred.PASSWORD);
        account.setGivenName(cred.FIRSTNAME);
        account.setSurname(cred.LASTNAME);
        AccountStore store = app.getDefaultAccountStore();
        Directory directory = stormpathClient.getResource(store.getHref(), Directory.class);
        directory.createAccount(account);
	    
	    User user = new User();
        user.setEmail(cred.EMAIL);
        user.setUsername(cred.USERNAME);
        user.setFirstName(cred.FIRSTNAME);
        user.setLastName(cred.LASTNAME);
        user.setStormpathHref(account.getHref());
        
	    UserSession session = new UserSession();
	    session.setUser(user);
	    return session;
	}
	
	private boolean areUsersEqual(User a, User b) {
	    if (a.getEmail().equals(b.getEmail())
	            && a.getUsername().equals(b.getUsername()))
	        return true;
	    
	    return false;	         
	}
    
    @Test
    public void updateUserUpdatesUserSession() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                UserCredentials a = new UserCredentials("auser", "P4ssword", "a@sagebase.org", "afirstname", "alastname");
                UserCredentials b = new UserCredentials("buser", "P4ssword", "b@sagebase.org", "bfirstname", "blastname");
                
                UserSession session = constructUserSession(a);
                User user = TestUtils.constructTestUser(b);
                
                service.updateUser(user, session);
                assertTrue("User has been updated.", areUsersEqual(user, session.getUser()));
            }
        });
    }
    
}
