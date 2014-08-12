package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserProfile;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.resource.ResourceException;

public class UserProfileServiceImpl implements UserProfileService {

    private Client stormpathClient;
    
    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }

    @Override
    public UserProfile createUserFromAccount(Account account) {
        try {
            UserProfile user;
            user = new UserProfile();
            user.setEmail(account.getEmail());
            user.setFirstName(account.getGivenName());
            user.setLastName(account.getSurname());
            user.setUsername(account.getUsername());
            user.setStormpathHref(account.getHref());

            return user;

        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), 500);
        } catch (NullPointerException ne) {
            throw new BridgeServiceException("Account object is null", 500);
        }
    }

    @Override
    public void updateUser(UserProfile updatedUser, UserProfile currentUser) {
        if (!UserProfile.isValidUser(updatedUser)) {
            throw new BridgeServiceException(
                    "Proposed user update has at least one null field (user model is incomplete).", 400);
        }
        try {
            Account account = stormpathClient.getResource(currentUser.getStormpathHref(), Account.class);
            account.setGivenName(updatedUser.getFirstName());
            account.setSurname(updatedUser.getLastName());
            account.setUsername(updatedUser.getUsername());
            account.save();
        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), re.getStatus());
        }
    }

}
