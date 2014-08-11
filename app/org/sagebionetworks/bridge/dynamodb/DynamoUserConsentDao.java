package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.ConsentAlreadyExistsException;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoUserConsentDao implements UserConsentDao {

    private static final long NOT_WITHDRAW_YET = 0L;

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void giveConsent(String healthCode, StudyConsent studyConsent, ResearchConsent researchConsent) {
        try {
            DynamoUserConsent consent = new DynamoUserConsent(healthCode, studyConsent);
            consent.setName(researchConsent.getName());
            consent.setBirthdate(researchConsent.getBirthdate());
            consent.setGive(DateTime.now(DateTimeZone.UTC).getMillis());
            consent.setWithdraw(NOT_WITHDRAW_YET);
            mapper.save(consent);
        } catch (ConditionalCheckFailedException e) {
            throw new ConsentAlreadyExistsException();
        }
    }

    @Override
    public void withdrawConsent(String healthCode, StudyConsent studyConsent) {
        // Delete the consent
        DynamoUserConsent consentToDelete = new DynamoUserConsent(healthCode, studyConsent);
        consentToDelete.setWithdraw(NOT_WITHDRAW_YET);
        consentToDelete = mapper.load(consentToDelete);
        mapper.delete(consentToDelete);
        // Save with the withdraw time stamp for audit
        DynamoUserConsent consentToWithdraw = new DynamoUserConsent(consentToDelete);
        consentToWithdraw.setWithdraw(DateTime.now(DateTimeZone.UTC).getMillis());
        consentToWithdraw.setVersion(null);
        mapper.save(consentToWithdraw);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consentToDelete = new DynamoUserConsent(healthCode, studyConsent);
        consentToDelete.setWithdraw(NOT_WITHDRAW_YET);
        return mapper.load(consentToDelete) != null;
    }

    @Override
    public ResearchConsent getConsentSignature(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consent = new DynamoUserConsent(healthCode, studyConsent);
        consent = mapper.load(consent);
        
        return new ResearchConsent(consent.getName(), consent.getBirthdate());
    }
    
    
}
