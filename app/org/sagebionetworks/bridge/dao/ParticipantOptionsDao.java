package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.accounts.AllUserOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.UserOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ParticipantOptionsDao {
    
    /**
     * Set an option for a participant. Value cannot be null.
     * @param studyIdentifier
     * @param healthCode
     * @param option
     * @param value
     */
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value);
     
    /**
     * Get the options for a single participant. 
     */
    public UserOptionsLookup getOptions(String healthCode);
    
    /**
     * Get all options for all participants in a study. 
     */
    public AllUserOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier);
   
    /**
     * Clear an option for a participant.
     */
    public void deleteOption(String healthCode, ParticipantOption option);

    /**
     * Delete the entire record associated with this participant--used to delete users.
     */
    public void deleteAllOptions(String healthCode);
    
}
