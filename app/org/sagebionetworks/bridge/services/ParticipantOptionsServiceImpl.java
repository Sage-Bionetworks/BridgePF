package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.validators.Validate;

public class ParticipantOptionsServiceImpl implements ParticipantOptionsService {

    private ParticipantOptionsDao optionsDao;
    
    public void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }
    
    @Override
    public void setOption(Study study, String healthCode, Option option, String value) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        checkArgument(isNotBlank(value), Validate.CANNOT_BE_BLANK, "value");
        
        optionsDao.setOption(study, healthCode, option, value);
    }
    
    @Override
    public String getOption(String healthCode, Option option) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOption(healthCode, option);
    }

    @Override
    public boolean getBooleanOption(String healthCode, Option option) {
        String value = getOption(healthCode, option);
        return Boolean.valueOf(value);
    }
    
    public void deleteAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }
    
    @Override
    public void deleteOption(String healthCode, Option option) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        optionsDao.deleteOption(healthCode, option);
    }

    @Override
    public Map<Option, String> getAllParticipantOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode), Validate.CANNOT_BE_BLANK, "healthCode");
        
        return optionsDao.getAllParticipantOptions(healthCode);
    }

    @Override
    public OptionLookup getOptionForAllStudyParticipants(Study study, Option option) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOptionForAllStudyParticipants(study, option);
    }

}
