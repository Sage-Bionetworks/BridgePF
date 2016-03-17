package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.accounts.AllParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptions;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ParticipantOptionsDao {
    
    /**
     * Set an option for a participant. None of the values can be null.
     */
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value);
     
    /**
     * Set one or more participant options at once. If a field in ParticipantOptions is null, it will not be changed. 
     * If the value exists, then it will be update. 
     * @param options
     */
    public void setAllOptions(StudyIdentifier studyIdentifier, String healthCode, ParticipantOptions options);
    
    /**
     * Get all the options for a single participant. 
     */
    public ParticipantOptionsLookup getOptions(String healthCode);
    
    /**
     * Get all the options for all participants in a study. 
     */
    public AllParticipantOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier);
   
    /**
     * Clear a single option for a participant.
     */
    public void deleteOption(String healthCode, ParticipantOption option);

    /**
     * Delete the entire set of options associated with this participant. Used to delete a user.
     */
    public void deleteAllOptions(String healthCode);
    
}
