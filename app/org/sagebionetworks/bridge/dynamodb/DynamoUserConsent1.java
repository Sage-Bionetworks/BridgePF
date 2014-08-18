package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "UserConsent1")
public class DynamoUserConsent1 implements DynamoTable {

    // Schema attributes
    private String healthCodeStudyConsent; // composite hash key: <health-code>:<key-of-study-consent>
    private Long signedOn;
    private Long version;

    // Value attributes
    private Boolean dataSharing;
    private String name;
    private String birthdate;
    private String studyKey;       // study-consent composite key copied over to avoid parsing
    private long consentCreatedOn; // study-consent composite key copied over to avoid parsing

    public DynamoUserConsent1() {}

    DynamoUserConsent1(String healthCode, StudyConsent consent) {
        studyKey = consent.getStudyKey();
        consentCreatedOn = consent.getCreatedOn();
        healthCodeStudyConsent = healthCode + ":" + studyKey + ":" + consentCreatedOn;
    }

    DynamoUserConsent1(DynamoUserConsent1 consent) {
        healthCodeStudyConsent = consent.healthCodeStudyConsent;
        signedOn = consent.signedOn;
        version = consent.version;
        name = consent.name;
        birthdate = consent.birthdate;
        studyKey = consent.studyKey;
        consentCreatedOn = consent.consentCreatedOn;
    }

    @DynamoDBHashKey
    public String getHealthCodeStudyConsent() {
        return healthCodeStudyConsent;
    }
    public void setHealthCodeStudyConsent(String healthCodeStudyConsent) {
        this.healthCodeStudyConsent = healthCodeStudyConsent;
    }

    @DynamoDBAttribute
    public Long getSignedOn() {
        return signedOn;
    }
    public void setSignedOn(Long timestamp) {
        this.signedOn = timestamp;
    }

    @DynamoDBAttribute
    public Boolean getDataSharing() {
        return dataSharing;
    }
    public void setDataSharing(Boolean dataSharing) {
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
    public long getConsentTimestamp() {
        return consentCreatedOn;
    }
    public void setConsentTimestamp(long consentTimestamp) {
        this.consentCreatedOn = consentTimestamp;
    }
    
    @DynamoDBAttribute(attributeName = "name")
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    @DynamoDBAttribute(attributeName = "birthdate")
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
