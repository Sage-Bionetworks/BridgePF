package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@Component
public class DynamoUserConsentDao implements UserConsentDao {

    private DynamoDBMapper mapper;

    @Resource(name = "userConsentDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserConsent giveConsent(String healthCode, StudyConsent studyConsent, long signedOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyConsent);
        checkArgument(signedOn > 0L);

        UserConsent activeConsent = getActiveUserConsent(healthCode, new StudyIdentifierImpl(studyConsent.getStudyKey()));
        if (activeConsent != null) {
            throw new BridgeServiceException("Consent already exists.", HttpStatus.SC_CONFLICT);
        }
        
        DynamoUserConsent3 consent = new DynamoUserConsent3(healthCode, studyConsent.getStudyKey());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(signedOn);
        mapper.save(consent);

        return consent;
    }

    @Override
    public void withdrawConsent(String healthCode, StudyIdentifier studyIdentifier, long withdrewOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        checkArgument(withdrewOn > 0L);
        
        DynamoUserConsent3 activeConsent = (DynamoUserConsent3)getActiveUserConsent(healthCode, studyIdentifier);
        if (activeConsent == null) {
            throw new BridgeServiceException("Consent not found.", HttpStatus.SC_NOT_FOUND);
        }
        activeConsent.setWithdrewOn(withdrewOn);
        mapper.save(activeConsent);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        
        return getActiveUserConsent(healthCode, studyIdentifier) != null;
    }

    @Override
    public UserConsent getActiveUserConsent(String healthCode, StudyIdentifier studyIdentifier) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());

        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.NULL);
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withQueryFilterEntry("withdrewOn", condition)
            .withHashKeyValues(hashKey);
        
        List<DynamoUserConsent3> results = mapper.query(DynamoUserConsent3.class, query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier, long signedOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        checkArgument(signedOn > 0L);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());
        hashKey.setSignedOn(signedOn);
        
        DynamoUserConsent3 consent = mapper.load(hashKey);
        if (consent == null) {
            throw new BridgeServiceException("Consent not found.", HttpStatus.SC_NOT_FOUND);   
        }
        return consent;
    }
    
    @Override
    public List<UserConsent> getUserConsentHistory(String healthCode, StudyIdentifier studyIdentifier) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());
        
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withHashKeyValues(hashKey);

        return mapper.query(DynamoUserConsent3.class, query).stream().map(consent -> {
            return (UserConsent)consent;
        }).collect(Collectors.toList());
    }

    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier);
        
        Condition studyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        
        Condition withdrewCondition = new Condition().withComparisonOperator(ComparisonOperator.NULL);
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        scan.setConsistentRead(true);
        scan.addFilterCondition("studyIdentifier", studyCondition);
        scan.addFilterCondition("withdrewOn", withdrewCondition);
        
        return mapper.scan(DynamoUserConsent3.class, scan).stream().map(consent -> {
            return consent.getHealthCode();
        }).collect(Collectors.toSet()).size();
    }

    @Override
    public void deleteAllConsents(String healthCode, StudyIdentifier studyIdentifier) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(studyIdentifier);
        
        List<UserConsent> consents = getUserConsentHistory(healthCode, studyIdentifier);
        if (!consents.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consents);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}
