package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
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
    public User updateProfile(User user, UserProfile profile) {
        try {
            // When saving to StormPath, use <EMPTY> rather than an empty string, but otherwise
            // preserve null or empty string in the UI. It's not an error.
            Account account = stormpathClient.getResource(user.getStormpathHref(), Account.class);
            account.setGivenName(profile.getFirstNameWithEmptyString());
            account.setSurname(profile.getLastNameWithEmptyString());
            account.save();
            user.setFirstName(profile.getFirstName());
            user.setLastName(profile.getLastName());
            return user;
        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage());
        }
    }

}
