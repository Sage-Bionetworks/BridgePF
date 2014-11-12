package org.sagebionetworks.bridge.services;

import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.Study;

public interface ParticipantOptionsService {
    /**
     * Set an option for a participant. Value cannot be null.
     * @param healthCode
     * @param option
     * @param value
     */
    public void setOption(Study study, String healthCode, Option option, String value);
    
    /**
     * Get an option for a participant. Returns the default value for the option if the option 
     * has never been set for this participant (which may be null).
     * @param healthCode
     * @param option
     * @return
     */
    public String getOption(String healthCode, Option option);
    
    /**
     * Get a participant option as a boolean
     * @param healthCode
     * @param option
     * @return true or false (false if not set)
     */
    public boolean getBooleanOption(String healthCode, Option option);
    
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
    public void deleteOption(String healthCode, Option option);

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not.
     * @param healthCode
     * @param option
     * @return
     */
    public Map<Option,String> getAllParticipantOptions(String healthCode);
    
    /**
     * Get a map of all health codes to all values for an option (null if never set), for a 
     * given study. Useful for export and other batch tasks.
     * @param studyKey
     * @param option
     * @return
     */
    public OptionLookup getOptionForAllStudyParticipants(Study study, Option option);

}
