package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.ConsentAlreadyExistsException;
import org.sagebionetworks.bridge.dao.ConsentNotFoundException;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoUserConsentDao implements UserConsentDao {

    private final Logger logger = LoggerFactory.getLogger(DynamoUserConsentDao.class);

    private static final long NOT_WITHDRAW_YET = 0L;

    private DynamoDBMapper mapperOld;
    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent.class));
        mapperOld = new DynamoDBMapper(client, mapperConfig);
        mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent2.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void giveConsent(String healthCode, StudyConsent consent, ConsentSignature consentSignature) {
        giveConsentNew(healthCode, consent, consentSignature);
        giveConsentOld(healthCode, consent, consentSignature);
    }

    @Override
    public void withdrawConsent(String healthCode, StudyConsent consent) {
        withdrawConsentNew(healthCode, consent);
        withdrawConsentOld(healthCode, consent);
    }

    @Override
    public Long getConsentCreatedOn(String healthCode, String studyKey) {
        return getConsentCreatedOnNew(healthCode, studyKey);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyConsent consent) {
        boolean hasConsented = hasConsentedNew(healthCode, consent);
        boolean hasConsentedOld = hasConsentedOld(healthCode, consent);
        if (hasConsentedOld != hasConsented) {
            logger.error("Old, new consent inconsistent.");
        }
        return hasConsented;
    }

    @Override
    public ConsentSignature getConsentSignature(String healthCode, StudyConsent consent) {
        ConsentSignature signature = getConsentSignatureNew(healthCode, consent);
        ConsentSignature signatureOld = getConsentSignatureOld(healthCode, consent);
        if (signature != null && !signature.equals(signatureOld)) {
            logger.error("Old, new consent signature inconsistent.");
        }
        if (signature == null && signatureOld != null) {
            logger.error("Old, new consent inconsistent.");
        }
        return signature;
    }

    @Override
    public void resumeSharing(String healthCode, StudyConsent consent) {
        DynamoUserConsent2 userConsent = new DynamoUserConsent2(healthCode, consent);
        userConsent = mapper.load(userConsent);
        if (userConsent == null) {
            throw new ConsentNotFoundException();
        }
        userConsent.setDataSharing(true);
        mapper.save(userConsent);
    }

    @Override
    public void suspendSharing(String healthCode, StudyConsent consent) {
        DynamoUserConsent2 userConsent = new DynamoUserConsent2(healthCode, consent);
        userConsent = mapper.load(userConsent);
        if (userConsent == null) {
            throw new ConsentNotFoundException();
        }
        userConsent.setDataSharing(false);
        mapper.save(userConsent);
    }

    @Override
    public boolean isSharingData(String healthCode, StudyConsent consent) {
        DynamoUserConsent2 userConsent = new DynamoUserConsent2(healthCode, consent);
        userConsent = mapper.load(userConsent);
        return (userConsent != null && userConsent.getDataSharing());
    }

    // Old

    void giveConsentOld(String healthCode, StudyConsent studyConsent, ConsentSignature researchConsent) {
        try {
            DynamoUserConsent consent = new DynamoUserConsent(healthCode, studyConsent);
            consent.setName(researchConsent.getName());
            consent.setBirthdate(researchConsent.getBirthdate());
            consent.setGive(DateTime.now(DateTimeZone.UTC).getMillis());
            consent.setWithdraw(NOT_WITHDRAW_YET);
            mapperOld.save(consent);
        } catch (ConditionalCheckFailedException e) {
            throw new ConsentAlreadyExistsException();
        }
    }

    private void withdrawConsentOld(String healthCode, StudyConsent studyConsent) {
        // Delete the consent
        DynamoUserConsent consentToDelete = new DynamoUserConsent(healthCode, studyConsent);
        consentToDelete.setWithdraw(NOT_WITHDRAW_YET);
        consentToDelete = mapperOld.load(consentToDelete);
        if (consentToDelete == null) {
            throw new ConsentNotFoundException();
        }
        mapperOld.delete(consentToDelete);
        // Save with the withdraw time stamp for audit
        DynamoUserConsent consentToWithdraw = new DynamoUserConsent(consentToDelete);
        consentToWithdraw.setWithdraw(DateTime.now(DateTimeZone.UTC).getMillis());
        consentToWithdraw.setVersion(null);
        mapperOld.save(consentToWithdraw);
    }

    private boolean hasConsentedOld(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consent = new DynamoUserConsent(healthCode, studyConsent);
        consent.setWithdraw(NOT_WITHDRAW_YET);
        return mapperOld.load(consent) != null;
    }

    private ConsentSignature getConsentSignatureOld(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consent = new DynamoUserConsent(healthCode, studyConsent);
        consent.setWithdraw(NOT_WITHDRAW_YET);
        consent = mapperOld.load(consent);
        if (consent == null) {
            throw new ConsentNotFoundException();
        }
        return new ConsentSignature(consent.getName(), consent.getBirthdate());
    }

    // New

    void giveConsentNew(String healthCode, StudyConsent studyConsent, ConsentSignature researchConsent) {
        try {
            DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
            consent = mapper.load(consent);
            if (consent == null) { // If the user has not consented yet
                consent = new DynamoUserConsent2(healthCode, studyConsent);
            }
            consent.setName(researchConsent.getName());
            consent.setBirthdate(researchConsent.getBirthdate());
            consent.setSignedOn(DateTime.now(DateTimeZone.UTC).getMillis());
            consent.setDataSharing(true);
            mapper.save(consent);
        } catch (ConditionalCheckFailedException e) {
            throw new ConsentAlreadyExistsException();
        }
    }

    void withdrawConsentNew(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consentToDelete = new DynamoUserConsent2(healthCode, studyConsent);
        consentToDelete = mapper.load(consentToDelete);
        if (consentToDelete == null) {
            throw new ConsentNotFoundException();
        }
        mapper.delete(consentToDelete);
    }

    Long getConsentCreatedOnNew(String healthCode, String studyKey) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyKey);
        consent = mapper.load(consent);
        return consent == null ? null : consent.getConsentCreatedOn();
    }

    boolean hasConsentedNew(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        return mapper.load(consent) != null;
    }

    ConsentSignature getConsentSignatureNew(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        if (consent == null) {
            throw new ConsentNotFoundException();
        }
        return new ConsentSignature(consent.getName(), consent.getBirthdate());
    }
}
