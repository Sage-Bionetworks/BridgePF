package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

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
@BridgeTypeName("UserConsent")
public class DynamoUserConsent3 implements UserConsent {
    
    private String healthCodeSubpopGuid;
    private String subpopGuid;
    private long consentCreatedOn;
    private long signedOn;
    // This field is Long (not long) because it can be null.
    private Long withdrewOn;
    private Long version;
    
    public DynamoUserConsent3() {}
    
    public DynamoUserConsent3(String healthCode, SubpopulationGuid subpopGuid) {
        setHealthCodeSubpopGuid(healthCode + ":" + subpopGuid.getGuid());
        this.subpopGuid = subpopGuid.getGuid();
    }
    
    @DynamoDBHashKey(attributeName = "healthCodeStudy")
    public String getHealthCodeSubpopGuid() {
        return healthCodeSubpopGuid;
    }
    public void setHealthCodeSubpopGuid(String healthCodeSubpopGuid) {
        this.healthCodeSubpopGuid = healthCodeSubpopGuid;
        if (healthCodeSubpopGuid != null && healthCodeSubpopGuid.contains(":")) {
            this.subpopGuid = healthCodeSubpopGuid.split(":")[1];
        }
    }
    @DynamoDBIgnore
    public String getHealthCode() {
        return (healthCodeSubpopGuid != null && healthCodeSubpopGuid.contains(":")) ?
                healthCodeSubpopGuid.split(":")[0] : null;
    }
    @Override
    @DynamoDBAttribute(attributeName="studyIdentifier")
    public String getSubpopulationGuid() {
        return subpopGuid;
    }
    public void setSubpopulationGuid(String subpopGuid) {
        this.subpopGuid = subpopGuid;
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
        return String.format("DynamoUserConsent3 [healthCodeSubpopGuid=%s, subpopGuid=%s, consentCreatedOn=%s, version=%s, signedOn=%s, withdrewOn=%s]",
                healthCodeSubpopGuid, subpopGuid, consentCreatedOn, version, signedOn, withdrewOn);
    }
    
}
