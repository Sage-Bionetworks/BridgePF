package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;

public interface UserProfileService {

    public User updateProfile(User caller, UserProfile profile);
    
}
