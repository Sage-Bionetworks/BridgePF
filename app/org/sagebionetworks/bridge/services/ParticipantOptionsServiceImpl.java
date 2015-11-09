package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

@Component
public class ParticipantOptionsServiceImpl implements ParticipantOptionsService {

    private static final Joiner SET_JOINER = Joiner.on(",");
    private static final Splitter SET_SPLITTER = Splitter.on(",");
    
    private ParticipantOptionsDao optionsDao;
    
    @Autowired
    public final void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }
    
    private void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        checkArgument(isNotBlank(value));
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    private String getOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        return optionsDao.getOption(healthCode, option);
    }
    
    @Override
    public void setSharingScope(StudyIdentifier studyIdentifier, String healthCode, SharingScope option) {
        setOption(studyIdentifier, healthCode, ParticipantOption.SHARING_SCOPE, option.name());
    }

    @Override
    public SharingScope getSharingScope(String healthCode) {
        String value = getOption(healthCode, ParticipantOption.SHARING_SCOPE);
        return Enum.valueOf(SharingScope.class, value);
    }

    @Override
    public void setDataGroups(StudyIdentifier studyIdentifier, String healthCode, Set<String> dataGroups) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(dataGroups);
        
        String value = SET_JOINER.join(dataGroups);
        setOption(studyIdentifier, healthCode, ParticipantOption.DATA_GROUPS, value);
    }

    @Override
    public Set<String> getDataGroups(String healthCode) {
        String value = getOption(healthCode, ParticipantOption.DATA_GROUPS);
        Set<String> dataGroups = Sets.newHashSet();
        if (isNotBlank(value)) {
            for (String group : SET_SPLITTER.split(value)) {
                dataGroups.add(group);
            }
        }
        return dataGroups;
    }

    @Override
    public void setEmailNotifications(StudyIdentifier studyIdentifier, String healthCode, boolean option) {
        setOption(studyIdentifier, healthCode, ParticipantOption.EMAIL_NOTIFICATIONS, Boolean.toString(option));
    }

    @Override
    public boolean getEmailNotifications(String healthCode) {
        String value = getOption(healthCode, ParticipantOption.EMAIL_NOTIFICATIONS);
        return Boolean.valueOf(value);
    }

    @Override
    public void setExternalIdentifier(StudyIdentifier studyIdentifier, String healthCode, String externalId) {
        setOption(studyIdentifier, healthCode, ParticipantOption.EXTERNAL_IDENTIFIER, externalId);
    }

    @Override
    public String getExternalIdentifier(String healthCode) {
        return getOption(healthCode, ParticipantOption.EXTERNAL_IDENTIFIER);
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
