package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.accounts.UserConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * This version of the consent will accept a withdrewOn timestamp, and the range key is set to the signedOn 
 * value so there can be multiple records, returned in the order they were created by participant.
 *
 */
@DynamoDBTable(tableName = "UserConsent3")
@BridgeTypeName("UserConsent")
public class DynamoUserConsent3 implements UserConsent {

    // The only timestamp that can be null is withdrewOn.
    private String healthCodeStudy;
    private String healthCode;
    private String studyIdentifier;
    private long consentCreatedOn;
    private long signedOn;
    private Long withdrewOn;
    private Long version;
    
    @DynamoDBHashKey
    public String getHealthCodeStudy() {
        return healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCodeStudy) {
        this.healthCodeStudy = healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCode, String studyIdentifier) {
        this.healthCode = healthCode;
        this.studyIdentifier = studyIdentifier;
        this.healthCodeStudy = healthCode + ":" + studyIdentifier;
    }
    
    @DynamoDBAttribute
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        setHealthCodeStudy(healthCode, getStudyIdentifier());
    }

    @Override
    @DynamoDBAttribute
    public String getStudyIdentifier() {
        return studyIdentifier;
    }
    public void setStudyIdentifier(String studyIdentifier) {
        setHealthCodeStudy(getHealthCode(), studyIdentifier);
    }

    @Override
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    public void setConsentCreatedOn(long consentCreatedOn) {
        this.consentCreatedOn = consentCreatedOn;
    }

    @Override
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getSignedOn() {
        return signedOn;
    }
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
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
        return String.format("DynamoUserConsent3 [healthCode=%s, studyIdentifier=%s, consentCreatedOn=%s, version=%s, signedOn=%s, withdrewOn=%s]",
                healthCode, studyIdentifier, consentCreatedOn, version, signedOn, withdrewOn);
    }
    
}
