package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "NotificationRegistration")
public class DynamoNotificationRegistration implements NotificationRegistration {

    private String healthCode;
    private String guid;
    private NotificationProtocol protocol;
    private String endpoint;
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

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public NotificationProtocol getProtocol() {
        // Previous versions of this table did not specify protocol and assumed "application" by default. If protocol
        // is not specified, return "application".
        return protocol != null ? protocol : NotificationProtocol.APPLICATION;
    }

    /** {@inheritDoc} */
    @Override
    public void setProtocol(NotificationProtocol protocol) {
        this.protocol = protocol;
    }

    // A previous version of this table called this "endpointARN". Retain the same name for backwards compatibility
    // with the existing table.
    /** {@inheritDoc} */
    @DynamoDBAttribute(attributeName = "endpointARN")
    @Override
    public String getEndpoint() {
        return endpoint;
    }

    /** {@inheritDoc} */
    @Override
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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
