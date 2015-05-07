package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;

public interface UserProfileService {

    public UserProfile getProfile(Study study, String email);
    
    public User updateProfile(Study study, User caller, UserProfile profile);
    
    public void sendStudyParticipantRoster(Study study);
    
    public UserProfile profileFromAccount(Study study, Account account);
    
}
