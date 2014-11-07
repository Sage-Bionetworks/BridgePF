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
    public void setOption(Study study, String healthDataCode, Option option, String value) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(isNotBlank(healthDataCode), Validate.CANNOT_BE_BLANK, "healthDataCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        checkArgument(isNotBlank(value), Validate.CANNOT_BE_BLANK, "value");
        
        optionsDao.setOption(study, healthDataCode, option, value);
    }
    
    @Override
    public String getOption(String healthDataCode, Option option) {
        checkArgument(isNotBlank(healthDataCode), Validate.CANNOT_BE_BLANK, "healthDataCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOption(healthDataCode, option);
    }

    @Override
    public boolean getBooleanOption(String healthDataCode, Option option) {
        String value = getOption(healthDataCode, option);
        return Boolean.valueOf(value);
    }
    
    public void deleteAllParticipantOptions(String healthDataCode) {
        checkArgument(isNotBlank(healthDataCode), Validate.CANNOT_BE_BLANK, "healthDataCode");
        
        optionsDao.deleteAllParticipantOptions(healthDataCode);
    }
    
    @Override
    public void deleteOption(String healthDataCode, Option option) {
        checkArgument(isNotBlank(healthDataCode), Validate.CANNOT_BE_BLANK, "healthDataCode");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        optionsDao.deleteOption(healthDataCode, option);
    }

    @Override
    public Map<Option, String> getAllParticipantOptions(String healthDataCode) {
        checkArgument(isNotBlank(healthDataCode), Validate.CANNOT_BE_BLANK, "healthDataCode");
        
        return optionsDao.getAllParticipantOptions(healthDataCode);
    }

    @Override
    public OptionLookup getOptionForAllStudyParticipants(Study study, Option option) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(option, Validate.CANNOT_BE_NULL, "option");
        
        return optionsDao.getOptionForAllStudyParticipants(study, option);
    }

}
