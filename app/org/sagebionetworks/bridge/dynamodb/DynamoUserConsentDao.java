package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.UserConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoUserConsentDao implements UserConsentDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent2.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void giveConsent(String healthCode, StudyConsent consent, ConsentSignature consentSignature) {
        giveConsent2(healthCode, consent, consentSignature);
    }

    @Override
    public void withdrawConsent(String healthCode, StudyConsent consent) {
        withdrawConsent2(healthCode, consent);
    }

    @Override
    public Long getConsentCreatedOn(String healthCode, String studyKey) {
        return getConsentCreatedOn2(healthCode, studyKey);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyConsent consent) {
        boolean hasConsented = hasConsented2(healthCode, consent);
        return hasConsented;
    }

    @Override
    public UserConsent getUserConsent(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        return mapper.load(consent);
    }
    
    @Override
    public ConsentSignature getConsentSignature(String healthCode, StudyConsent consent) {
        ConsentSignature signature = getConsentSignature2(healthCode, consent);
        return signature;
    }

    void giveConsent2(String healthCode, StudyConsent studyConsent, ConsentSignature researchConsent) {
        DynamoUserConsent2 consent = null;
        try {
            consent = new DynamoUserConsent2(healthCode, studyConsent);
            consent = mapper.load(consent);
            if (consent == null) { // If the user has not consented yet
                consent = new DynamoUserConsent2(healthCode, studyConsent);
            }
            consent.setName(researchConsent.getName());
            consent.setBirthdate(researchConsent.getBirthdate());
            consent.setSignedOn(DateTime.now(DateTimeZone.UTC).getMillis());
            mapper.save(consent);
        } catch (ConditionalCheckFailedException e) {
            throw new EntityAlreadyExistsException(consent);
        }
    }

    void withdrawConsent2(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consentToDelete = new DynamoUserConsent2(healthCode, studyConsent);
        consentToDelete = mapper.load(consentToDelete);
        if (consentToDelete == null) {
            return;
        }
        mapper.delete(consentToDelete);
    }

    Long getConsentCreatedOn2(String healthCode, String studyKey) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyKey);
        consent = mapper.load(consent);
        return consent == null ? null : consent.getConsentCreatedOn();
    }

    boolean hasConsented2(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        return mapper.load(consent) != null;
    }

    ConsentSignature getConsentSignature2(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        if (consent == null) {
            throw new EntityNotFoundException(DynamoUserConsent2.class);
        }
        return new ConsentSignature(consent.getName(), consent.getBirthdate());
    }
}
