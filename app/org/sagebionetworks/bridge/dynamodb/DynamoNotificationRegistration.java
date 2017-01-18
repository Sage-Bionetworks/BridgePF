package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "NotificationRegistration")
public class DynamoNotificationRegistration implements NotificationRegistration {

    private String healthCode;
    private String guid;
    private String endpointARN;
    private String deviceId;
    private String osName;
    private long createdOn;
    private long modifiedOn;
    
    @Override
    @DynamoDBHashKey
    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @Override
    @DynamoDBRangeKey
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Override
    @JsonIgnore
    public String getEndpointARN() {
        return endpointARN;
    }

    @Override
    public void setEndpointARN(String endpointARN) {
        this.endpointARN = endpointARN;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String getOsName() {
        return osName;
    }

    @Override
    public void setOsName(String osName) {
        this.osName = osName;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getCreatedOn() {
        return createdOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}
