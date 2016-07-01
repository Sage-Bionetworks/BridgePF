package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final static Logger LOG = LoggerFactory.getLogger(DynamoUserConsentDao.class);
    
    private DynamoDBMapper mapper;

    @Resource(name = "userConsentDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserConsent giveConsent(String healthCode, SubpopulationGuid subpopGuid, long consentCreatedOn, long signedOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(subpopGuid);
        checkArgument(signedOn > 0L);

        UserConsent activeConsent = getActiveUserConsent(healthCode, subpopGuid);
        if (activeConsent != null && activeConsent.getConsentCreatedOn() == consentCreatedOn) {
            // We don't throw EntityAlreadyExistsException here because UserConsent isn't an object in the API
            throw new BridgeServiceException("UserConsent already exists.", HttpStatus.SC_CONFLICT);
        }
        DynamoUserConsent3 consent = new DynamoUserConsent3(healthCode, subpopGuid);
        consent.setConsentCreatedOn(consentCreatedOn);
        consent.setSignedOn(signedOn);
        
        // If for some reason the user is giving consent at the same signedOn timestamp as an existing, withdrawn record, 
        // then this method would fail. Although we take steps to prevent this (particularly when repairing consents), it is 
        // still happening. This appears to be a bug or a timing issue. Copying version over provents concurrency exception and 
        // effectively re-consents the user at the given signedOn time.
        DynamoUserConsent3 existingConsent = mapper.load(consent);
        if (existingConsent != null) {
            LOG.info("Found an existing consent, copying version, existing: " + existingConsent + ", new consent: " + consent);
            consent.setVersion(existingConsent.getVersion());
        }
        mapper.save(consent);
        return consent;
    }

    @Override
    public void withdrawConsent(String healthCode, SubpopulationGuid subpopGuid, long withdrewOn) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(subpopGuid);
        checkArgument(withdrewOn > 0L);
        
        // In case a conflict has occurred where two consents are active for a single subpopulation, find 
        // all of them and withdraw from all of them.
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withHashKeyValues(hashKey)
            .withQueryFilterEntry("withdrewOn", new Condition().withComparisonOperator(ComparisonOperator.NULL));
        
        List<DynamoUserConsent3> results = mapper.query(DynamoUserConsent3.class, query);
        if (results.isEmpty()) {
            throw new EntityNotFoundException(UserConsent.class);
        }
        for (DynamoUserConsent3 consent : results) {
            consent.setWithdrewOn(withdrewOn);
            mapper.save(consent);
        }
    }

    @Override
    public boolean hasConsented(String healthCode, SubpopulationGuid subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        return getActiveUserConsent(healthCode, subpopGuid) != null;
    }

    @Override
    public UserConsent getActiveUserConsent(String healthCode, SubpopulationGuid subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);

        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withScanIndexForward(false)
            .withQueryFilterEntry("withdrewOn", new Condition().withComparisonOperator(ComparisonOperator.NULL))
            .withHashKeyValues(hashKey);
        
        List<DynamoUserConsent3> results = mapper.query(DynamoUserConsent3.class, query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public UserConsent getUserConsent(String healthCode, SubpopulationGuid subpopGuid, long signedOn) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        checkArgument(signedOn > 0L);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);
        hashKey.setSignedOn(signedOn);
        
        DynamoUserConsent3 consent = mapper.load(hashKey);
        if (consent == null) {
            throw new EntityNotFoundException(UserConsent.class);   
        }
        return consent;
    }
    
    @Override
    public List<UserConsent> getUserConsentHistory(String healthCode, SubpopulationGuid subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, subpopGuid);
        
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withHashKeyValues(hashKey);

        return mapper.query(DynamoUserConsent3.class, query).stream()
                .map(consent -> (UserConsent)consent).collect(Collectors.toList());
    }
    
    @Override
    public void deleteAllConsents(String healthCode, SubpopulationGuid subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        List<UserConsent> consents = getUserConsentHistory(healthCode, subpopGuid);
        if (!consents.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consents);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public Set<String> getParticipantHealthCodes(SubpopulationGuid subpopGuid) {
        checkNotNull(subpopGuid);
        
        // Note that although the mapper converts the studyIdentifier column name to 
        // subpopulationGuid, we have to use the existing column name here
        DynamoDBScanExpression scan = new DynamoDBScanExpression()
                .withFilterConditionEntry("studyIdentifier", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue(subpopGuid.getGuid())))
                .withFilterConditionEntry("withdrewOn", new Condition()
                        .withComparisonOperator(ComparisonOperator.NULL));
        
        return mapper.scan(DynamoUserConsent3.class, scan).stream()
            .map(DynamoUserConsent3::getHealthCode)
            .collect(Collectors.toSet());
    }
}
