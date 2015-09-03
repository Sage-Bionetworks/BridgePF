package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "UserConsent2")
@BridgeTypeName("UserConsent")
public class DynamoUserConsent2 implements UserConsent {

    // Schema attributes
    private String healthCodeStudy; // <health-code>:<study-key>
    private Long version;           // Version for optimistic locking

    // Value attributes
    private long signedOn;          // Time stamp is epoch time in milliseconds
    private boolean dataSharing;    // Whether the user agrees to share data for the study

    // Composite key parts copied over to avoid parsing
    private String healthCode;
    private String studyKey;
    private long consentCreatedOn;

    public DynamoUserConsent2() {}

    // Constructor to create a hash-key object
    DynamoUserConsent2(String healthCode, String studyKey) {
        this.healthCode = healthCode;
        this.studyKey = studyKey;
        healthCodeStudy = healthCode + ":" + studyKey;
    }

    // Constructor to create a hash-key object
    DynamoUserConsent2(String healthCode, StudyConsent consent) {
        this(healthCode, consent.getStudyKey());
        consentCreatedOn = consent.getCreatedOn();
    }

    // Copy constructor
    DynamoUserConsent2(DynamoUserConsent2 consent) {
        healthCodeStudy = consent.healthCodeStudy;
        signedOn = consent.signedOn;
        version = consent.version;
        healthCode = consent.healthCode;
        studyKey = consent.studyKey;
        consentCreatedOn = consent.consentCreatedOn;
    }

    @DynamoDBHashKey
    public String getHealthCodeStudy() {
        return healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCodeStudy) {
        this.healthCodeStudy = healthCodeStudy;
    }

    /**
     * Consent time stamp. Epoch time in milliseconds.
     */
    @DynamoDBAttribute
    public long getSignedOn() {
        return signedOn;
    }
    public void setSignedOn(long timestamp) {
        this.signedOn = timestamp;
    }

    @DynamoDBAttribute
    public boolean getDataSharing() {
        return dataSharing;
    }
    public void setDataSharing(boolean dataSharing) {
        this.dataSharing = dataSharing;
    }

    @DynamoDBAttribute
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @DynamoDBAttribute
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    public void setConsentCreatedOn(long consentCreatedOn) {
        this.consentCreatedOn = consentCreatedOn;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "DynamoUserConsent2 [version=" + version + ", signedOn=" + signedOn + ", dataSharing=" + dataSharing
                + ", studyKey=" + studyKey + ", consentCreatedOn=" + consentCreatedOn + "]";
    }
}
