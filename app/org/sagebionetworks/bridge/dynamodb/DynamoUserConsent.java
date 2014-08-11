package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "UserConsent")
public class DynamoUserConsent implements DynamoTable {

    private String healthCodeStudy; // <health-code>:<study-key>:<consent-timestamp>
    private String studyKey;
    private long consentTimestamp;  // Adding this comment to deploy again.
    private Long give;              // Timestamp when the consent is given
    private Long withdraw;          // Timestamp when the consent is withdrawn
    private String name;
    private String birthdate;
    private Long version;

    public DynamoUserConsent() {}

    DynamoUserConsent(String healthCode, StudyConsent consent) {
        studyKey = consent.getStudyKey();
        consentTimestamp = consent.getTimestamp();
        healthCodeStudy = healthCode + ":" + studyKey + ":" + consentTimestamp;
    }

    DynamoUserConsent(DynamoUserConsent consent) {
        name = consent.name;
        birthdate = consent.birthdate;
        healthCodeStudy = consent.healthCodeStudy;
        studyKey = consent.studyKey;
        consentTimestamp = consent.consentTimestamp;
        give = consent.give;
        withdraw = consent.withdraw;
        version = consent.version;
    }
    
    @DynamoDBHashKey
    public String getHealthCodeStudy() {
        return healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCodeStudy) {
        this.healthCodeStudy = healthCodeStudy;
    }

    @DynamoDBAttribute
    public Long getGive() {
        return give;
    }
    public void setGive(Long give) {
        this.give = give;
    }

    @DynamoDBRangeKey
    public Long getWithdraw() {
        return withdraw;
    }
    public void setWithdraw(Long withdraw) {
        this.withdraw = withdraw;
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
