package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "UserConsent2")
public class DynamoUserConsent2 implements DynamoTable {

    // Schema attributes
    private String healthCodeStudy; // <health-code>:<study-key>
    private Long version;           // Version for optimistic locking

    // Value attributes
    private long signedOn;          // Time stamp is epoch time in milliseconds
    private boolean dataSharing;    // Whether the user agrees to share data for the study

    // User consent signature
    private String name;
    private String birthdate;

    // Study-consent composite key copied over to avoid parsing
    private String studyKey;
    private long consentCreatedOn;

    public DynamoUserConsent2() {}

    // Constructor to create a hash-key object
    DynamoUserConsent2(String healthCode, StudyConsent consent) {
        studyKey = consent.getStudyKey();
        consentCreatedOn = consent.getCreatedOn();
        healthCodeStudy = healthCode + ":" + studyKey;
    }

    // Copy constructor
    DynamoUserConsent2(DynamoUserConsent2 consent) {
        healthCodeStudy = consent.healthCodeStudy;
        signedOn = consent.signedOn;
        version = consent.version;
        name = consent.name;
        birthdate = consent.birthdate;
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
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    @DynamoDBAttribute
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }
    public void setConsentCreatedOn(long consentCreatedOn) {
        this.consentCreatedOn = consentCreatedOn;
    }

    @DynamoDBAttribute
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @DynamoDBAttribute
    public String getBirthdate() {
        return this.birthdate;
    }
    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
