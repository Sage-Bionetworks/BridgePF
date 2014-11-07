package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.Study;

public interface ParticipantOptionsDao {

    public enum Option {
        DATA_SHARING("true");
        
        private String defaultValue;
        private Option(String defaultValue) {
            this.defaultValue = defaultValue;
        }
        public String getDefaultValue() {
            return defaultValue;
        }
    }
    
    /**
     * Set an option for a participant. Value cannot be null.
     * @param healthDataCode
     * @param option
     * @param value
     */
    public void setOption(Study study, String healthDataCode, Option option, String value);
     
    /**
     * Get an option for a participant. Returns defaultValue (which can be null for a specific 
     * option, see the Option enum class) if the value has never been set.
     * @param healthDataCode
     * @param option
     * @return
     */
    public String getOption(String healthDataCode, Option option);
    
    /**
     * Clear an option for a participant.
     * @param healthDataCode
     * @param option
     */
    public void deleteOption(String healthDataCode, Option option);

    /**
     * Delete the entire record associated with this participant--used to delete users.
     * @param healthDataCode
     */
    public void deleteAllParticipantOptions(String healthDataCode);
    
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
    public OptionLookup getOptionForAllStudyParticipants(Study study, Option option);
    
}
