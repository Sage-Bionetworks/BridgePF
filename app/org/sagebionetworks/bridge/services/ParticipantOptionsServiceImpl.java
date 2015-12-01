package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

@Component
public class ParticipantOptionsServiceImpl implements ParticipantOptionsService {
    
    private ParticipantOptionsDao optionsDao;
    
    @Autowired
    final void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }

    @Override
    public Map<ParticipantOption, String> getAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        return optionsDao.getAllParticipantOptions(healthCode);
    }

    @Override
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option) {
        checkNotNull(studyIdentifier);
        checkNotNull(option);
        
        return optionsDao.getOptionForAllStudyParticipants(studyIdentifier, option);
    }

    @Override
    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, Boolean.toString(value));
    }

    @Override
    public boolean getBoolean(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    @Override
    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    @Override
    public String getString(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        return optionsDao.getOption(healthCode, option);
    }

    @Override
    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);

        String result = (value == null) ? null : value.name();
        optionsDao.setOption(studyIdentifier, healthCode, option, result);
    }

    @Override
    public <T extends Enum<T>> T getEnum(String healthCode, ParticipantOption option, Class<T> enumType) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        checkNotNull(enumType);

        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? null : Enum.valueOf(enumType, value);
    }
    
    @Override
    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, BridgeUtils.setToCommaList(value));
    }
    
    @Override
    public Set<String> getStringSet(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        String value = optionsDao.getOption(healthCode, option);
        return (value == null) ? Sets.newHashSet() : Sets.newHashSet(Splitter.on(",").split(value));
    }
    
    @Override
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }
    
    @Override
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.deleteOption(healthCode, option);
    }
    
}
