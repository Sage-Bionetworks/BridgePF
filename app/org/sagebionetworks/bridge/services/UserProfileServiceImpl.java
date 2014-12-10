package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;

import com.stormpath.sdk.account.Account;

public class UserProfileServiceImpl implements UserProfileService {

    private AuthenticationService authService;
    
    public void setAuthenticationService(AuthenticationService authService) {
        this.authService = authService;
    }

    @Override
    public User updateProfile(User user, UserProfile profile) {
        Account account = authService.getAccount(user.getEmail());
        account.setGivenName(profile.getFirstNameWithEmptyString());
        account.setSurname(profile.getLastNameWithEmptyString());
        account.save();
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        return user;
    }

}
