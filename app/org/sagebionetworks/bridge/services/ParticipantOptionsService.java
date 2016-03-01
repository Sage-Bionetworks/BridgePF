package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.accounts.AllUserOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.UserOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

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
    public UserOptionsLookup getOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        return optionsDao.getOptions(healthCode);
    }
    
    /**
     * Get all options for all participants in a study. For batch operations on all participants in a 
     * study that require multiple entries per participant from the participant options table, this is 
     * the most efficient way to get those values.
     * 
     * @param studyIdentifier
     *   
     */
    public AllUserOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier) {
        return optionsDao.getOptionsForAllParticipants(studyIdentifier);
    }

    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, Boolean.toString(value));
    }

    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        String result = (value == null) ? null : value.name();
        optionsDao.setOption(studyIdentifier, healthCode, option, result);
    }

    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, BridgeUtils.setToCommaList(value));
    }

    /**
     * Set a string set preserving the order the keys were inserted into the set.
     */
    public void setOrderedStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option,
            LinkedHashSet<String> value) {
        setStringSet(studyIdentifier, healthCode, option, value);
    }
    
    /**
     * Delete the entire record associated with a participant in the study and all options.
     * @param healthCode
     */
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        optionsDao.deleteAllOptions(healthCode);
    }
    
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.deleteOption(healthCode, option);
    }
    
}
