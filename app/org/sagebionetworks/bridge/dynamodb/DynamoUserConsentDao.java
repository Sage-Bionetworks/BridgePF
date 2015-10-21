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
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Sets;

/**
 * Currently migrating to a new consent table. The options for keeping the two tables in sync:
 * 
 *  table3 succeeds, table2 succeeds - fine
 *  table3 fails,    --              - fine, return error
 *  table3 succeeds, table2 fails    - we can fix this in migration, or undo the table3 record.
 *                                     Also, return an error
 */
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

        // The logic is being changed here. You cannot update the signing date on an active
        // consent record if it exists. Throw an exception if it does.
        DynamoUserConsent2 consent2 = getUserConsent2(healthCode, studyConsent.getStudyKey());
        if (consent2 != null) {
            throw new EntityAlreadyExistsException(null, "Consent already exists.");
        }
        
        // save 3 then do 2, undo 3 if 2 fails
        DynamoUserConsent3 consent3 = getUserConsent3(healthCode, studyConsent.getStudyKey());
        if (consent3 == null) {
            consent3 = new DynamoUserConsent3(healthCode, studyConsent.getStudyKey());
            consent3.setConsentCreatedOn(studyConsent.getCreatedOn());
            consent3.setSignedOn(signedOn);
            mapperV3.save(consent3);
        }
        if (consent2 == null) {
            try {
                consent2 = new DynamoUserConsent2(healthCode, studyConsent.getStudyKey());
                consent2.setConsentCreatedOn(studyConsent.getCreatedOn());
                consent2.setSignedOn(signedOn);
                mapperV2.save(consent2);
            } catch(Exception e) {
                // compensate and then rethrow exception
                mapperV3.delete(consent3);
                throw e;
            }
        }
        return consent2;
    }

    @Override
    public boolean withdrawConsent(String healthCode, StudyIdentifier studyIdentifier) {
        // In first step of migration, if user withdraws consent, we delete UserConsent3 record. Once all records are
        // synchronized and we're ready to switch over, they will be marked with a withdrawal timestamp and queries 
        // will be adjusted accordingly. We will also need to coordinate this timestamp with the historical signature 
        // record in Stormpath.
        DynamoUserConsent2 consent2 = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        DynamoUserConsent3 consent3 = getUserConsent3(healthCode, studyIdentifier.getIdentifier());
        
        if (consent2 == null && consent3 == null) {
            return false;
        }
        // delete 3 then delete 2, re-save 3 if 2 fails
        if (consent3 != null) {
            mapperV3.delete(consent3);
        }
        if (consent2 != null) {
            try {
                mapperV2.delete(consent2);    
            } catch(Exception e) {
                // compensate for delete failure. Need to vacate the version attribute
                consent3.setVersion(null);
                mapperV3.save(consent3);
                throw e;
            }
        }
        return true;
    }

    @Override
    public boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier) {
        return getUserConsent2(healthCode, studyIdentifier.getIdentifier()) != null;
    }

    @Override
    public UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier) {
        return getUserConsent2(healthCode, studyIdentifier.getIdentifier());
    }

    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        scan.setConsistentRead(true);

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);

        // We need the count of unique study participants, users may end up signing
        // more than one version of the consent.
        Set<String> healthCodes = Sets.newHashSet();
        List<DynamoUserConsent2> mappings = mapperV2.scan(DynamoUserConsent2.class, scan);
        mappings.stream().forEach(consent -> healthCodes.add(consent.getHealthCode()));
        return healthCodes.size();
    }

    @Override
    public void deleteConsentRecords(String healthCode, StudyIdentifier studyIdentifier) {
        List<DynamoUserConsent3> consentsV3 = getAllUserConsentRecords3(healthCode, studyIdentifier.getIdentifier());
        if (!consentsV3.isEmpty()) {
            List<FailedBatch> failures = mapperV3.batchDelete(consentsV3);
            BridgeUtils.ifFailuresThrowException(failures);
        }
        DynamoUserConsent2 consentV2 = getUserConsent2(healthCode, studyIdentifier.getIdentifier());
        if (consentV2 != null) {
            try {
                mapperV2.delete(consentV2);    
            } catch(Exception e) {
                // vacate the versions
                List<DynamoUserConsent3> consents = consentsV3.stream().map(consent -> {
                    consent.setVersion(null);
                    return consent;
                }).collect(Collectors.toList());
                mapperV3.batchSave(consents);
                throw e;
            }
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
