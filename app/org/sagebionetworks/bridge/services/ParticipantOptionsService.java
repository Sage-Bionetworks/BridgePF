package org.sagebionetworks.bridge.services;

import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.models.Study;

public interface ParticipantOptionsService {
    /**
     * Set an option for a participant. Value cannot be null.
     * @param healthDataCode
     * @param option
     * @param value
     */
    public void setOption(Study study, String healthDataCode, Option option, String value);
    
    /**
     * Get an option for a participant. Returns null if the value has never been set.
     * @param healthDataCode
     * @param option
     * @return
     */
    public String getOption(String healthDataCode, Option option);
    
    /**
     * Get an option for a participant. Returns the default value if the option has never
     * been set.
     * @param healthDataCode
     * @param option
     * @param defaultValue
     * @return
     */
    public String getOption(String healthDataCode, Option option, String defaultValue);
    
    /**
     * Get a participant option as a boolean
     * @param healthDataCode
     * @param option
     * @return true or false (false if not set)
     */
    public boolean getBooleanOption(String healthDataCode, Option option);
    
    /**
     * Get a participant option as a boolean
     * @param healthDataCode
     * @param option
     * @param defaultValue
     * @return true or false (or the defaultValue if not set)
     */
    public boolean getBooleanOption(String healthDataCode, Option option, boolean defaultValue);
    
    /**
     * Delete the entire record associated with a participant in the study (for deleting users).
     * @param healthDataCode
     */
    public void deleteAllParticipantOptions(String healthDataCode);
    
    /**
     * Clear an option for a participant.
     * @param healthDataCode
     * @param option
     */
    public void deleteOption(String healthDataCode, Option option);

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not.
     * @param healthDataCode
     * @param option
     * @return
     */
    public Map<Option,String> getAllParticipantOptions(String healthDataCode);
    
    /**
     * Get a map of all health codes to all values for an option (null if never set), for a 
     * given study. Useful for export and other batch tasks.
     * @param studyKey
     * @param option
     * @return
     */
    public Map<String,String> getOptionForAllStudyParticipants(Study study, Option option);

}
