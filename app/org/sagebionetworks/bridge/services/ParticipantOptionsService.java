package org.sagebionetworks.bridge.services;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Provides type-safe access to all the options that are set for a healthCode. All the values can also be exported as 
 * strings and still be intelligible (for example when exporting to Synapse). 
 *
 */
public interface ParticipantOptionsService {
    
    /**
     * Set whether or not a user should be contacted via email. True until set to be false (do not contact user via email).
     * @param studyIdentifier
     * @param healthCode
     * @param option
     */
    public void setEmailNotifications(StudyIdentifier studyIdentifier, String healthCode, boolean option);
    
    /**
     * Get whether or not a user should be contacted via email. Returns true if this value has not been set to false.
     * @param healthCode
     * @param option
     * @return
     */
    public boolean getEmailNotifications(String healthCode);
    
    /**
     * Set an external identifier for this participant. Value cannot be null.
     * @param studyIdentifier
     * @param healthCode
     * @param externalId
     */
    public void setExternalIdentifier(StudyIdentifier studyIdentifier, String healthCode, String externalId);

    /**
     * Get the external identifier for this participant. Returns null if not present.
     * @param healthCode
     * @return
     */
    public String getExternalIdentifier(String healthCode);
   
    /**
     * Set the scope of sharing option.
     * @param studyIdentifier
     * @param healthCode
     * @param option
     */
    public void setSharingScope(StudyIdentifier studyIdentifier, String healthCode, SharingScope option);
    
    /**
     * Get the scope of sharing option as an enumeration. Returns the default value for the option if the option 
     * has never been set for this participant (which may be null).
     * @param healthCode
     * @param option
     * @param cls
     */
    public SharingScope getSharingScope(String healthCode);
    
    /**
     * Set the data groups for this participant. The group are stored as a comma-separated list of values and exported
     * verbatim.
     * @param studyIdentifier
     * @param healthCode
     * @param dataGroups
     */
    public void setDataGroups(StudyIdentifier studyIdentifier, String healthCode, Set<String> dataGroups);
    
    /**
     * Get the data group options for this participant as a string set. Will return an empty set if the option has 
     * not been set for this participant.
     * @param healthCode
     * @return
     */
    public Set<String> getDataGroups(String healthCode);
    
    /**
     * Delete the entire record associated with a participant in the study (for deleting users).
     * @param healthCode
     */
    public void deleteAllParticipantOptions(String healthCode);
    
    /**
     * Clear an option for a participant.
     * @param healthCode
     * @param option
     */
    public void deleteOption(String healthCode, ParticipantOption option);

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not.
     * @param healthCode
     * @param option
     * @return
     */
    public Map<ParticipantOption,String> getAllParticipantOptions(String healthCode);
    
    /**
     * Get a map of all health codes to all values for an option (null if never set), for a 
     * given study. Useful for export and other batch tasks.
     * @param studyIdentifier
     * @param option
     * @return
     */
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option);

}
