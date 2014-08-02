package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "UserConsent")
public class DynamoUserConsent implements DynamoTable {

    private String healthCodeStudy; // <health-code>:<study-key>
    private long consentTimestamp;
    private Long give;              // Timestamp when the consent is given
    private Long withdraw;          // Timestamp when the consent is withdrawn
    private Long version;

    @DynamoDBHashKey
    public String getHealthCodeStudy() {
        return healthCodeStudy;
    }
    public void setHealthCodeStudy(String healthCodeStudy) {
        this.healthCodeStudy = healthCodeStudy;
    }

    @DynamoDBRangeKey
    public long getConsentTimestamp() {
        return consentTimestamp;
    }
    public void setConsentTimestamp(long consentTimestamp) {
        this.consentTimestamp = consentTimestamp;
    }

    @DynamoDBAttribute
    public Long getGive() {
        return give;
    }
    public void setGive(Long give) {
        this.give = give;
    }

    @DynamoDBAttribute
    public Long getWithdraw() {
        return withdraw;
    }
    public void setWithdraw(Long withdraw) {
        this.withdraw = withdraw;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
