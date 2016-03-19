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
import org.sagebionetworks.bridge.models.accounts.AllParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

@Component
public class ParticipantOptionsService {
    
    private ParticipantOptionsDao optionsDao;
    
    @Autowired
    final void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }

    /**
     * Get all options and their values for a participant in a lookup object with type-safe 
     * accessors. If a value is not set, the value will be null in the map. A lookup object 
     * will be returned whether any values have been set for this participant or not. 
     */
    public ParticipantOptionsLookup getOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        return optionsDao.getOptions(healthCode);
    }
    
    /**
     * Get all options for all participants in a study, in a lookup object that always returns a 
     * ParticipantOptionsLookup object (event for healthCodes that have no options saved). For batch 
     * operations on all participants in a study this is the most efficient way to get those values.
     */
    public AllParticipantOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier) {
        return optionsDao.getOptionsForAllParticipants(studyIdentifier);
    }

    /**
     * Persist a boolean participant option.
     */
    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, Boolean.toString(value));
    }

    /**
     * Persist a string participant option.
     */
    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    /**
     * Persist an enumerated participant option.
     */
    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        String result = (value == null) ? null : value.name();
        optionsDao.setOption(studyIdentifier, healthCode, option, result);
    }

    /**
     * Persist a string set option. The keys in the string set are persisted in the order they are retrieved from a set, 
     * and returned in that same order.
     */
    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, BridgeUtils.setToCommaList(value));
    }

    /**
     * Persist a string set option with a set of keys that are ordered by their insertion in the set.
     */
    public void setOrderedStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, LinkedHashSet<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        setStringSet(studyIdentifier, healthCode, option, value);
    }
    
    public void setAllOptions(StudyIdentifier studyIdentifier, String healthCode, Map<ParticipantOption,String> options) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(options);
        
        optionsDao.setAllOptions(studyIdentifier, healthCode, options);
    }
    
    /**
     * Delete the entire record associated with a participant in the study and all options.
     */
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        optionsDao.deleteAllOptions(healthCode);
    }
    
    /**
     * Delete a single option for a participant. 
     */
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.deleteOption(healthCode, option);
    }
    
}
