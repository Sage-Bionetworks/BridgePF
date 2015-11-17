package org.sagebionetworks.bridge.services;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Provides type-safe access to all the options that can be persisted for a health code, as well as validation. 
 * The values are saved as strings and can be exported as such (in spreadsheets, Synapse, and so forth).
 */
public interface ParticipantOptionsService {
    
    /**
     * Set whether or not a user should be contacted via email. By default true  (we can contact user via email).
     * @param studyIdentifier
     * @param healthCode
     * @param option
     */
    public void setEmailNotifications(StudyIdentifier studyIdentifier, String healthCode, boolean option);
    
    /**
     * Get whether or not a user should be contacted via email.
     * @param healthCode
     * @param option
     * @return
     */
    public boolean getEmailNotifications(String healthCode);
    
    /**
     * Set an external identifier for this participant. By default, this value is null.
     * @param studyIdentifier
     * @param healthCode
     * @param externalId
     */
    public void setExternalIdentifier(StudyIdentifier studyIdentifier, String healthCode, ExternalIdentifier externalId);

    /**
     * Get the external identifier for this participant. 
     * @param healthCode
     * @return
     */
    public String getExternalIdentifier(String healthCode);
   
    /**
     * Set the scope of sharing option. By default, set to NO_SHARING.
     * @param studyIdentifier
     * @param healthCode
     * @param option
     */
    public void setSharingScope(StudyIdentifier studyIdentifier, String healthCode, SharingScope option);
    
    /**
     * Get the sharing scope for this health code.
     * @param healthCode
     */
    public SharingScope getSharingScope(String healthCode);
    
    /**
     * Set the data groups for this participant. The group are stored as a comma-separated list of values and exported
     * verbatim. By default, an empty set.
     * @param studyIdentifier
     * @param healthCode
     * @param dataGroups
     */
    public void setDataGroups(StudyIdentifier studyIdentifier, String healthCode, DataGroups dataGroups);
    
    /**
     * Get the data group options for this participant as a string set. 
     * @param healthCode
     * @return
     */
    public Set<String> getDataGroups(String healthCode);
    
    /**
     * Delete the entire record associated with a participant in the study and all options.
     * @param healthCode
     */
    public void deleteAllParticipantOptions(String healthCode);
    
    /**
     * Clear one of the options for a participant. Will fallback to the default value.
     * @param healthCode
     * @param option
     */
    public void deleteOption(String healthCode, ParticipantOption option);

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not.
     * @param healthCode
     * @return
     */
    public Map<ParticipantOption,String> getAllParticipantOptions(String healthCode);
    
    /**
     * Get a map of all health codes to all values for an option (default value if not set for a given 
     * participant), for a given study. Useful for export and other batch tasks.
     * @param studyIdentifier
     * @param option
     * @return
     */
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option);

}
