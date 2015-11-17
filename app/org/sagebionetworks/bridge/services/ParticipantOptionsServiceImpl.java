package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

@Component
public class ParticipantOptionsServiceImpl implements ParticipantOptionsService {
    
    private ParticipantOptionsDao optionsDao;
    
    @Autowired
    final void setParticipantOptionsDao(ParticipantOptionsDao participantOptionsDao) {
        this.optionsDao = participantOptionsDao;
    }
    
    private void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        optionsDao.setOption(studyIdentifier, healthCode, option, value);
    }

    private String getOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        return optionsDao.getOption(healthCode, option);
    }
    
    @Override
    public void setSharingScope(StudyIdentifier studyIdentifier, String healthCode, SharingScope option) {
        setOption(studyIdentifier, healthCode, SHARING_SCOPE, option.name());
    }

    @Override
    public SharingScope getSharingScope(String healthCode) {
        String value = getOption(healthCode, SHARING_SCOPE);
        return Enum.valueOf(SharingScope.class, value);
    }

    @Override
    public void setDataGroups(StudyIdentifier studyIdentifier, String healthCode, Set<String> dataGroups) {
        checkNotNull(dataGroups);
        // The rest are checked in setOption
        
        String value = BridgeUtils.setToCommaList(dataGroups.stream()
                .filter(StringUtils::isNotBlank).collect(toSet()));
        
        setOption(studyIdentifier, healthCode, DATA_GROUPS, value);
    }

    @Override
    public Set<String> getDataGroups(String healthCode) {
        String value = getOption(healthCode, DATA_GROUPS);
        return BridgeUtils.commaListToSet(value);
    }

    @Override
    public void setEmailNotifications(StudyIdentifier studyIdentifier, String healthCode, boolean option) {
        setOption(studyIdentifier, healthCode, EMAIL_NOTIFICATIONS, Boolean.toString(option));
    }

    @Override
    public boolean getEmailNotifications(String healthCode) {
        String value = getOption(healthCode, EMAIL_NOTIFICATIONS);
        return Boolean.valueOf(value);
    }

    @Override
    public void setExternalIdentifier(StudyIdentifier studyIdentifier, String healthCode, String externalId) {
        setOption(studyIdentifier, healthCode, EXTERNAL_IDENTIFIER, externalId);
    }

    @Override
    public String getExternalIdentifier(String healthCode) {
        return getOption(healthCode, EXTERNAL_IDENTIFIER);
    }
    
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

}
