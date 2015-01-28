package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

public interface UserProfileService {

    public UserProfile getProfile(String email);
    
    public User updateProfile(User caller, UserProfile profile);
    
    public List<StudyParticipant> getStudyParticipants(Study study);
    
}
