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
    private Long timestamp;
    private Long version;

    // Value attributes
    private Boolean dataSharing;
    private String name;
    private String birthdate;
    private String studyKey;       // study-consent composite key copied over to avoid parsing
    private long consentTimestamp; // study-consent composite key copied over to avoid parsing

    public DynamoUserConsent1() {}

    DynamoUserConsent1(String healthCode, StudyConsent consent) {
        studyKey = consent.getStudyKey();
        consentTimestamp = consent.getTimestamp();
        healthCodeStudyConsent = healthCode + ":" + studyKey + ":" + consentTimestamp;
    }

    DynamoUserConsent1(DynamoUserConsent1 consent) {
        healthCodeStudyConsent = consent.healthCodeStudyConsent;
        timestamp = consent.timestamp;
        version = consent.version;
        name = consent.name;
        birthdate = consent.birthdate;
        studyKey = consent.studyKey;
        consentTimestamp = consent.consentTimestamp;
    }

    @DynamoDBHashKey
    public String getHealthCodeStudyConsent() {
        return healthCodeStudyConsent;
    }
    public void setHealthCodeStudyConsent(String healthCodeStudyConsent) {
        this.healthCodeStudyConsent = healthCodeStudyConsent;
    }

    @DynamoDBAttribute
    public Long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
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
        return consentTimestamp;
    }
    public void setConsentTimestamp(long consentTimestamp) {
        this.consentTimestamp = consentTimestamp;
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
