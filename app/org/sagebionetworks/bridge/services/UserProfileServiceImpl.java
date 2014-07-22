package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

public class UserProfileServiceImpl implements UserProfileService {

	private Client stormpathClient;
	
	public void setStormpathClient(Client stormpathClient) {
	    this.stormpathClient = stormpathClient;
	}
	
    @Override
    public User createUserFromAccount(Account account) {
        User user;
        user = new User();
        user.setEmail(         account.getEmail() );
        user.setFirstName(     account.getGivenName() );
        user.setLastName(      account.getSurname() );
        user.setUsername(      account.getUsername() );
        user.setStormpathHref( account.getHref() );
        
        return user;
    }
	
    @Override
    public User getUser(UserSession session) {
        if (session == null) throw new BridgeServiceException("No active user session.", 401);
        
        return session.getUser();
    }

    @Override
    public void updateUser(User updatedUser, UserSession session) {
        if (session == null)                throw new BridgeServiceException(401);
        if (!User.isValidUser(updatedUser)) throw new BridgeServiceException(400);
        
        User currentUser         = session.getUser();
        Application app          = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(currentUser.getEmail()));
        AccountList accounts     = app.getAccounts(criteria);
        
        int count = 0;
        for (Account account : accounts) count++;
        if (count == 0) throw new BridgeServiceException("No matched users.", 500);
        if (count >  1) throw new BridgeServiceException("Updated user matches more than one possible user.", 500);
        
        for (Account account : accounts) {
            account.setEmail(     updatedUser.getEmail());
            account.setGivenName( updatedUser.getFirstName());
            account.setSurname(   updatedUser.getLastName());
            account.setUsername(  updatedUser.getUsername());
        }
        
        if (!accountUpdated(accounts, updatedUser)) throw new BridgeServiceException("Stormpath did not update.", 500);
        
        session.setUser(updatedUser);
    }
    
    /*
     * Checks that the account has been updated with the new user information.
     * Assumptions:
     *  - AccountList is of length 1.
     *  - updatedUser is a valid one.
     */
    private boolean accountUpdated(AccountList accounts, User updatedUser) {
        for (Account account : accounts)
            if (   account.getEmail()    .equalsIgnoreCase(updatedUser.getEmail())
                && account.getGivenName().equalsIgnoreCase(updatedUser.getFirstName())
                && account.getSurname()  .equalsIgnoreCase(updatedUser.getLastName()) 
                && account.getUsername() .equalsIgnoreCase(updatedUser.getUsername()) )
                return true;
        
        return false;
    }

}
