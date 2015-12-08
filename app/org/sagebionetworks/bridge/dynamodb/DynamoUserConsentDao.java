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
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
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

        UserConsent activeConsent = getActiveUserConsent(healthCode, studyConsent.getSubpopulationGuid());
        if (activeConsent != null && activeConsent.getConsentCreatedOn() == studyConsent.getCreatedOn()) {
            throw new BridgeServiceException("Consent already exists.", HttpStatus.SC_CONFLICT);
        }
        
        DynamoUserConsent3 consent = new DynamoUserConsent3(healthCode, studyConsent.getSubpopulationGuid());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(signedOn);
        mapper.save(consent);

        return consent;
    }

    @Override
    public void withdrawConsent(String healthCode, String subpopGuid, long withdrewOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(subpopGuid);
        checkArgument(withdrewOn > 0L);
        
        DynamoUserConsent3 activeConsent = (DynamoUserConsent3)getActiveUserConsent(healthCode, subpopGuid);
        if (activeConsent == null) {
            throw new BridgeServiceException("Consent not found.", HttpStatus.SC_NOT_FOUND);
        }
        activeConsent.setWithdrewOn(withdrewOn);
        mapper.save(activeConsent);
    }

    @Override
    public boolean hasConsented(String healthCode, String subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        return getActiveUserConsent(healthCode, subpopGuid) != null;
    }

    @Override
    public UserConsent getActiveUserConsent(String healthCode, String subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);

        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.NULL);
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withScanIndexForward(false)
            .withQueryFilterEntry("withdrewOn", condition)
            .withHashKeyValues(hashKey);
        
        List<DynamoUserConsent3> results = mapper.query(DynamoUserConsent3.class, query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public UserConsent getUserConsent(String healthCode, String subpopGuid, long signedOn) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        checkArgument(signedOn > 0L);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);
        hashKey.setSignedOn(signedOn);
        
        DynamoUserConsent3 consent = mapper.load(hashKey);
        if (consent == null) {
            throw new BridgeServiceException("Consent not found.", HttpStatus.SC_NOT_FOUND);   
        }
        return consent;
    }
    
    @Override
    public List<UserConsent> getUserConsentHistory(String healthCode, String subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);
        
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withHashKeyValues(hashKey);

        return mapper.query(DynamoUserConsent3.class, query).stream()
                .map(consent -> (UserConsent)consent).collect(Collectors.toList());
    }
    
    @Override
    public void deleteAllConsents(String healthCode, String subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        List<UserConsent> consents = getUserConsentHistory(healthCode, subpopGuid);
        if (!consents.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consents);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}
