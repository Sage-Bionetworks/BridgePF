package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.collect.Sets;

@Component
public class ParticipantOptionsService {
    
    private ParticipantOptionsDao optionsDao;
    
    @Autowired
    final void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not. 
     * @param healthCode
     * @return
     */
    public Map<ParticipantOption, String> getAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        return optionsDao.getAllParticipantOptions(healthCode);
    }
    
    /**
     * Get all options for all participants in a study. For batch operations on all participants in a 
     * study that require multiple entries per participant from the participant options table, this is 
     * the most efficient way to get those values.
     * 
     * @param studyIdentifier
     *   
     */
    public Map<ParticipantOption,OptionLookup> getAllOptionsForAllStudyParticipants(StudyIdentifier studyIdentifier) {
        return optionsDao.getAllOptionsForAllStudyParticipants(studyIdentifier);
    }

    /**
     * Get all options and their values set for a participant as a map of key/value pairs.
     * If a value is not set, the value will be null in the map. Map will be returned whether 
     * any values have been set for this participant or not.
     * @param healthCode
     * @return
     */
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option) {
        checkNotNull(studyIdentifier);
        checkNotNull(option);
        
        return optionsDao.getOptionForAllStudyParticipants(studyIdentifier, option);
    }

    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, Boolean.toString(value));
    }

    public boolean getBoolean(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    public String getString(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        return optionsDao.getOption(healthCode, option);
    }

    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        String result = (value == null) ? null : value.name();
        optionsDao.setOption(studyIdentifier, healthCode, option, result);
    }

    public <T extends Enum<T>> T getEnum(String healthCode, ParticipantOption option, Class<T> enumType) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        checkNotNull(enumType);

        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? null : Enum.valueOf(enumType, value);
    }
    
    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, BridgeUtils.setToCommaList(value));
    }
    
    public Set<String> getStringSet(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? Sets.newLinkedHashSet() : BridgeUtils.commaListToOrderedSet(value);
    }
    
    /**
     * Set a string set preserving the order the keys were inserted into the set.
     */
    public void setOrderedStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option,
            LinkedHashSet<String> value) {
        setStringSet(studyIdentifier, healthCode, option, value);
    }
    
    /**
     * Get a String set with the keys in the original order they were inserted, 
     * or in the order they are represented in a JSON array.
     */
    public LinkedHashSet<String> getOrderedStringSet(String healthCode, ParticipantOption option) {
        return (LinkedHashSet<String>)getStringSet(healthCode, option);
    }
    
    /**
     * Delete the entire record associated with a participant in the study and all options.
     * @param healthCode
     */
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }
    
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.deleteOption(healthCode, option);
    }
    
}
