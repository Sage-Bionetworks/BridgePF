package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.accounts.AllParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptions;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableMap;

@Component
public class DynamoParticipantOptionsDao implements ParticipantOptionsDao {

    private DynamoDBMapper mapper;
    
    @Resource(name = "participantOptionsDbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void setOption(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            options = new DynamoParticipantOptions();
        }
        options.setStudyKey(studyIdentifier.getIdentifier());
        options.setHealthCode(healthCode);
        options.getOptions().put(option.name(), value);
        mapper.save(options);
    }
    
    @Override
    public void setAllOptions(StudyIdentifier studyIdentifier, String healthCode, ParticipantOptions options) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(options);
        
        // A special case: if there's nothing to update, don't.
        if (options.hasNoUpdates()) {
            return;
        }
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions dynamoOptions = mapper.load(keyObject);
        if (dynamoOptions == null) {
            dynamoOptions = new DynamoParticipantOptions();
        }
        dynamoOptions.setStudyKey(studyIdentifier.getIdentifier());
        dynamoOptions.setHealthCode(healthCode);
        
        Map<String,String> optionsMap = dynamoOptions.getOptions();
        if (options.getSharingScope() != null) {
            optionsMap.put(SHARING_SCOPE.name(), options.getSharingScope().name());
        }
        if (options.getNotifyByEmail() != null) {
            optionsMap.put(EMAIL_NOTIFICATIONS.name(), options.getNotifyByEmail().toString());
        }
        if (options.getExternalId() != null) {
            optionsMap.put(EXTERNAL_IDENTIFIER.name(), options.getExternalId());
        }
        if (options.getDataGroups() != null) {
            optionsMap.put(DATA_GROUPS.name(), BridgeUtils.setToCommaList(options.getDataGroups()));
        }
        if (options.getLanguages() != null) {
            optionsMap.put(LANGUAGES.name(), BridgeUtils.setToCommaList(options.getLanguages()));
        }
        mapper.save(dynamoOptions);    
    }
    
    @Override
    public ParticipantOptionsLookup getOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options == null) {
            return new ParticipantOptionsLookup(ImmutableMap.of());
        }
        return new ParticipantOptionsLookup(options.getOptions());
    }
    
    @Override
    public void deleteAllOptions(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            mapper.delete(options);    
        }
    }

    @Override
    public void deleteOption(String healthCode, ParticipantOption option) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option);
        
        DynamoParticipantOptions keyObject = new DynamoParticipantOptions();
        keyObject.setHealthCode(healthCode);
        
        DynamoParticipantOptions options = mapper.load(keyObject);
        if (options != null) {
            options.getOptions().remove(option.name());
            mapper.save(options);
        }
    }

    @Override
    public AllParticipantOptionsLookup getOptionsForAllParticipants(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier);
        
        AllParticipantOptionsLookup allLookup = new AllParticipantOptionsLookup();
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);
        
        List<DynamoParticipantOptions> mappings = mapper.scan(DynamoParticipantOptions.class, scan);
        for (DynamoParticipantOptions mapping : mappings) {
            allLookup.put(mapping.getHealthCode(), new ParticipantOptionsLookup(mapping.getOptions()));
        }
        return allLookup;
    }

}
