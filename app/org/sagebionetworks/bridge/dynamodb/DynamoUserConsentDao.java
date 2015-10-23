package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
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
import com.google.common.collect.Sets;

@Component
public class DynamoUserConsentDao implements UserConsentDao {

    private DynamoDBMapper mapperV2;
    private DynamoDBMapper mapperV3;

    @Resource(name = "userConsentDdbMapper2")
    public final void setDdbMapper2(DynamoDBMapper mapper2) {
        this.mapperV2 = mapper2;
    }

    @Resource(name = "userConsentDdbMapper3")
    public final void setDdbMapper3(DynamoDBMapper mapper3) {
        this.mapperV3 = mapper3;
    }

    @Override
    public UserConsent giveConsent(String healthCode, StudyConsent studyConsent) {
        checkArgument(isNotBlank(healthCode), "Health code is blank or null");
        checkNotNull(studyConsent);

        // This will be passed in to the method when we coordinate Stormpath and DDB operations
        long signedOn = DateUtils.getCurrentMillisFromEpoch();

        // It doesn't currently matter which table your consent is in, you can't consent again 
        // if a record exists.
        UserConsent existingConsent = getUserConsent(healthCode, new StudyIdentifierImpl(studyConsent.getStudyKey()));
        if (existingConsent != null) {
            throw new BridgeServiceException("Consent already exists.", HttpStatus.SC_CONFLICT);
        }
        
        DynamoUserConsent3 consent = new DynamoUserConsent3(healthCode, studyConsent.getStudyKey());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(signedOn);
        mapperV3.save(consent);

        return consent;
    }

    @Override
    public boolean withdrawConsent(String healthCode, StudyIdentifier studyIdentifier) {
        // In first step of migration, if user withdraws consent, we delete both consent records. In the future we 
        // will updated UserConsent3 record with a timestamp, and these will not be returned by queries for an 
        // active consent.
        boolean hasWithdrawn = false;
        
        DynamoUserConsent2 consent2 = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        if (consent2 != null) {
            mapperV2.delete(consent2);
            hasWithdrawn = true;
        }
        DynamoUserConsent3 consent3 = getUserConsent3(healthCode, studyIdentifier.getIdentifier());
        if (consent3 != null) {
            mapperV3.delete(consent3);
            hasWithdrawn = true;
        }
        return hasWithdrawn;
    }

    @Override
    public boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier) {
        return getUserConsent(healthCode, studyIdentifier) != null;
    }

    @Override
    public UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier) {
        UserConsent consent = getUserConsent3(healthCode, studyIdentifier.getIdentifier());
        if (consent == null) {
            consent = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        }
        return consent;
    }

    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        scan.setConsistentRead(true);
        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);

        // Must find the complete set of unique health codes in both tables.
        Set<String> healthCodes = Sets.newHashSet();
        List<DynamoUserConsent2> mappings2 = mapperV2.scan(DynamoUserConsent2.class, scan);
        mappings2.stream().forEach(consent -> healthCodes.add(consent.getHealthCode()));
        
        // The name of the column has changed, create a different expression
        scan = new DynamoDBScanExpression();
        scan.setConsistentRead(true);
        condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyIdentifier", condition);
        
        List<DynamoUserConsent3> mappings3 = mapperV3.scan(DynamoUserConsent3.class, scan);
        mappings3.stream().forEach(consent -> healthCodes.add(consent.getHealthCode()));
        
        return healthCodes.size();
    }

    @Override
    public void deleteConsentRecords(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent2 consentV2 = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        if (consentV2 != null) {
            mapperV2.delete(consentV2);
        }
        List<DynamoUserConsent3> consentsV3 = getAllUserConsentRecords3(healthCode, studyIdentifier.getIdentifier());
        if (!consentsV3.isEmpty()) {
            List<FailedBatch> failures = mapperV3.batchDelete(consentsV3);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public boolean migrateConsent(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent2 consent2 = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        DynamoUserConsent3 consent3 = getUserConsent3(healthCode, studyIdentifier.getIdentifier());

        if (consent2 != null && consent3 == null) {
            DynamoUserConsent3 newConsent = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());
            newConsent.setConsentCreatedOn(consent2.getConsentCreatedOn());
            newConsent.setSignedOn(consent2.getSignedOn());
            mapperV3.save(newConsent);
            return true;
        }
        return false;
    }

    private DynamoUserConsent2 getUserConsent2(String healthCode, String studyIdentifier) {
        DynamoUserConsent2 hashKey = new DynamoUserConsent2(healthCode, studyIdentifier);

        return mapperV2.load(hashKey);
    }

    private DynamoUserConsent3 getUserConsent3(String healthCode, String studyIdentifier) {
        // This record has a range key so there are multiple consents. Get the first one, for now.
        List<DynamoUserConsent3> consents = getAllUserConsentRecords3(healthCode, studyIdentifier);
        
        return (consents.isEmpty()) ? null : consents.get(0);
    }
    
    private List<DynamoUserConsent3> getAllUserConsentRecords3(String healthCode, String studyIdentifier) {
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier);

        // Currently this is only one record. In the next phase of migration, remove scanIndexForward (maybe) and the limit.
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
                .withScanIndexForward(false)
                .withLimit(1)
                .withHashKeyValues(hashKey);

        return mapperV3.query(DynamoUserConsent3.class, query);
    }
}
