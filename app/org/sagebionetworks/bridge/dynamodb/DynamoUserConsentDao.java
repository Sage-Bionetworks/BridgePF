package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.ConsentAlreadyExistsException;
import org.sagebionetworks.bridge.dao.ConsentNotFoundException;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

public class DynamoUserConsentDao implements UserConsentDao {

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
    public void giveConsent(String healthCode, StudyConsent studyConsent, ConsentSignature researchConsent) {
        giveConsentOld(healthCode, studyConsent, researchConsent);
        giveConsentNew(healthCode, studyConsent, researchConsent);
    }

    @Override
    public void withdrawConsent(String healthCode, StudyConsent studyConsent) {
        withdrawConsentOld(healthCode, studyConsent);
        withdrawConsentNew(healthCode, studyConsent);
    }

    @Override
    public Long getConsentCreatedOn(String healthCode, String studyKey) {
        return getConsentCreatedOnNew(healthCode, studyKey);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyConsent studyConsent) {
        boolean hasConsentedOld = hasConsentedOld(healthCode, studyConsent);
        if (hasConsentedOld != hasConsentedNew(healthCode, studyConsent)) {
            // TODO: After backfill, log an error here
        }
        return hasConsentedOld;
    }

    @Override
    public ConsentSignature getConsentSignature(String healthCode, StudyConsent studyConsent) {
        ConsentSignature consentOld = getConsentSignatureOld(healthCode, studyConsent);
        if (consentOld != null && !consentOld.equals(getConsentSignatureNew(healthCode, studyConsent))) {
            // TODO: After backfill, log an error here
        }
        return consentOld;
    }

    @Override
    public void resumeSharing(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        if (consent == null) {
            throw new ConsentNotFoundException();
        }
        consent.setDataSharing(true);
        mapper.save(consent);
    }

    @Override
    public void suspendSharing(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        if (consent == null) {
            throw new ConsentNotFoundException();
        }
        consent.setDataSharing(false);
        mapper.save(consent);
    }

    @Override
    public boolean isSharingData(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        return (consent != null && consent.getDataSharing());
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

    @Override
    public int backfill() {
        int count = 0;
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        List<DynamoUserConsent> consentsOld = mapperOld.scan(DynamoUserConsent.class, scanExpression);
        for (DynamoUserConsent consentOld : consentsOld) {
            String hashKey = consentOld.getHealthCodeStudy();
            final String studyKey = consentOld.getStudyKey();
            final long consentTimestamp = consentOld.getConsentTimestamp();
            final String healthCode = hashKey.substring(0, hashKey.indexOf(":" + studyKey + ":" + consentTimestamp));
            final DynamoStudyConsent1 studyConsent = new DynamoStudyConsent1();
            studyConsent.setStudyKey(studyKey);
            studyConsent.setCreatedOn(consentTimestamp);
            if (hasConsentedOld(healthCode, studyConsent)) {
                if (!hasConsentedNew(healthCode, studyConsent)) {
                    DynamoUserConsent2 consentNew = new DynamoUserConsent2(healthCode, studyConsent);
                    consentNew.setDataSharing(true);
                    consentNew.setName(consentOld.getName());
                    consentNew.setBirthdate(consentOld.getBirthdate());
                    consentNew.setSignedOn(consentOld.getGive());
                    mapper.save(consentNew);
                    count++;
                }
            }
        }
        return count;
    }
}
