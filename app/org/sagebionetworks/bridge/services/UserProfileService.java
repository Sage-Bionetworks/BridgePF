package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.UserProfile;

import com.stormpath.sdk.account.Account;

public interface UserProfileService {

    public UserProfile createUserFromAccount(Account account);

    public void updateUser(UserProfile updatedUser, UserProfile currentUser);
    
}
