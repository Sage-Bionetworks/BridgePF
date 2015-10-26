package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.UserConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

/**
 * This version of the consent will accept a withdrewOn timestamp, and the range key is set to the signedOn 
 * value so there can be multiple records, returned in the order they were created by participant.
 *
 */
@DynamoDBTable(tableName = "UserConsent3")
public class DynamoUserConsent3 implements UserConsent {

    // The only timestamp that can be null is withdrewOn.
    private String healthCodeStudy;
    private String studyIdentifier;
    private long consentCreatedOn;
    private long signedOn;
    private Long withdrewOn;
    private Long version;
    
    public DynamoUserConsent3() {}
    
    public DynamoUserConsent3(String healthCode, String studyIdentifier) {
        setHealthCodeStudy(healthCode + ":" + studyIdentifier);
        this.studyIdentifier = studyIdentifier;
    }
    
    @DynamoDBHashKey
    public String getHealthCodeStudy() {
        return healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCodeStudy) {
        this.healthCodeStudy = healthCodeStudy;
    }
    @DynamoDBIgnore
    public String getHealthCode() {
        return (healthCodeStudy != null && healthCodeStudy.contains(":")) ?
                healthCodeStudy.split(":")[0] : null;
    }
    @Override
    @DynamoDBAttribute
    public String getStudyIdentifier() {
        return studyIdentifier;
    }
    public void setStudyIdentifier(String studyIdentifier) {
        this.studyIdentifier = studyIdentifier;
    }
    @Override
    @DynamoDBAttribute
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }
    public void setConsentCreatedOn(long consentCreatedOn) {
        this.consentCreatedOn = consentCreatedOn;
    }

    @Override
    @DynamoDBRangeKey
    public long getSignedOn() {
        return signedOn;
    }
    public void setSignedOn(long signedOn) {
        this.signedOn = signedOn;
    }
    
    @Override
    @DynamoDBAttribute
    public Long getWithdrewOn() {
        return withdrewOn;
    }
    public void setWithdrewOn(Long withdrewOn) {
        this.withdrewOn = withdrewOn;
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
        return String.format("DynamoUserConsent3 [healthCodeStudy=%s, studyIdentifier=%s, consentCreatedOn=%s, version=%s, signedOn=%s, withdrewOn=%s]",
                healthCodeStudy, studyIdentifier, consentCreatedOn, version, signedOn, withdrewOn);
    }
    
}
