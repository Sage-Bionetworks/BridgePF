package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;

import com.stormpath.sdk.account.Account;

public interface UserProfileService {

    public UserProfile getProfile(String email);
    
    public User updateProfile(User caller, UserProfile profile);
    
    public void sendStudyParticipantRoster(Study study);
    
    public UserProfile profileFromAccount(Account account);
    
}
