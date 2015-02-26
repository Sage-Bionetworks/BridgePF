package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.Validate;

public class ParticipantOptionsServiceImpl implements ParticipantOptionsService {

    private ParticipantOptionsDao optionsDao;
    
    public void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }
    
    @Override
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        checkArgument(isNotBlank(value), Validate.CANNOT_BE_BLANK, "value");
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }
    
    @Override
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, SharingScope option) {
        setOption(studyIdentifier, healthCode, ParticipantOption.SHARING_SCOPE, option.name());
    }
    
    @Override
    public String getOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOption(healthCode, option);
    }

    public SharingScope getSharingScope(String healthCode) {
        String value = getOption(healthCode, ParticipantOption.SHARING_SCOPE);
        return Enum.valueOf(SharingScope.class, value);
    }
    
    @Override
    public boolean getBooleanOption(String healthCode, ParticipantOption option) {
        String value = getOption(healthCode, option);
        return Boolean.valueOf(value);
    }
    
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }
    
    @Override
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        optionsDao.deleteOption(healthCode, option);
    }

    @Override
    public Map<ParticipantOption, String> getAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        
        return optionsDao.getAllParticipantOptions(healthCode);
    }

    @Override
    public OptionLookup getOptionForAllStudyParticipants(StudyIdentifier studyIdentifier, ParticipantOption option) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOptionForAllStudyParticipants(studyIdentifier, option);
    }

}
