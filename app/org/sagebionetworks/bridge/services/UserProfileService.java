package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;

public interface UserProfileService {

    public UserProfile getProfile(String email);
    
    public User updateProfile(User caller, UserProfile profile);
    
}
