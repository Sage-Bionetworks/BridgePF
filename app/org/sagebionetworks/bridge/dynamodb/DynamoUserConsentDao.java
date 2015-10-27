package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
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
        checkArgument(isNotBlank(healthCode), "Health code is blank or null");
        checkNotNull(studyConsent);

        // It doesn't currently matter which table your consent is in, you can't consent again 
        // if a record exists.
        UserConsent existingConsent = getActiveUserConsent(healthCode, new StudyIdentifierImpl(studyConsent.getStudyKey()));
        if (existingConsent != null) {
            throw new BridgeServiceException("Consent already exists.", HttpStatus.SC_CONFLICT);
        }
        
        DynamoUserConsent3 consent = new DynamoUserConsent3(healthCode, studyConsent.getStudyKey());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(signedOn);
        mapper.save(consent);

        return consent;
    }

    @Override
    public void withdrawConsent(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent3 existingConsent = (DynamoUserConsent3)getActiveUserConsent(healthCode, studyIdentifier);
        if (existingConsent == null) {
            throw new BridgeServiceException("Consent not found.", HttpStatus.SC_NOT_FOUND);
        }
        existingConsent.setWithdrewOn(DateTime.now().getMillis());
        mapper.save(existingConsent);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier) {
        return getActiveUserConsent(healthCode, studyIdentifier) != null;
    }

    @Override
    public UserConsent getActiveUserConsent(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());

        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.NULL);
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withQueryFilterEntry("withdrewOn", condition)
            .withHashKeyValues(hashKey);
        
        List<DynamoUserConsent3> results = mapper.query(DynamoUserConsent3.class, query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<UserConsent> getUserConsentHistory(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent3 hashKey = new DynamoUserConsent3(healthCode, studyIdentifier.getIdentifier());
        
        DynamoDBQueryExpression<DynamoUserConsent3> query = new DynamoDBQueryExpression<DynamoUserConsent3>()
            .withHashKeyValues(hashKey);

        return mapper.query(DynamoUserConsent3.class, query).stream().map(consent -> {
            return (UserConsent)consent;
        }).collect(Collectors.toList());
    }

    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        Condition cond1 = new Condition();
        cond1.withComparisonOperator(ComparisonOperator.EQ);
        cond1.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        
        Condition cond2 = new Condition();
        cond2.withComparisonOperator(ComparisonOperator.NULL);
        
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        scan.setConsistentRead(true);
        scan.addFilterCondition("studyIdentifier", cond1);
        scan.addFilterCondition("withdrewOn", cond2);
        
        return mapper.scan(DynamoUserConsent3.class, scan).stream().map(consent -> {
            return consent.getHealthCode();
        }).collect(Collectors.toSet()).size();
    }

    @Override
    public void deleteAllConsents(String healthCode, StudyIdentifier studyIdentifier) {
        List<UserConsent> consents = getUserConsentHistory(healthCode, studyIdentifier);
        if (!consents.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consents);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}
