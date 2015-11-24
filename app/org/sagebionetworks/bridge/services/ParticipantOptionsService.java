package org.sagebionetworks.bridge.services;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Provides type-safe access to all the options that can be persisted for a health code, as well as validation. 
 * The values are saved as strings and can be exported as such (in spreadsheets, Synapse, and so forth).
 */
public interface ParticipantOptionsService {
    
    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value);
    
    public boolean getBoolean(String healthCode, ParticipantOption option);
    
    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value);
    
    public String getString(String healthCode, ParticipantOption option);
    
    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value);
    
    public <T extends Enum<T>> T getEnum(String healthCode, ParticipantOption option, Class<T> enumType);
    
    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value);
    
    public Set<String> getStringSet(String healthCode, ParticipantOption option);
    
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
