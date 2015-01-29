package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;

public interface UserProfileService {

    public UserProfile getProfile(String email);
    
    public User updateProfile(User caller, UserProfile profile);
    
    public void sendStudyParticipantRoster(Study study);
    
}
