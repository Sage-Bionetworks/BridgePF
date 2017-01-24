package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.dynamodb.DynamoNotificationRegistration;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("NotificationRegistration")
@JsonDeserialize(as=DynamoNotificationRegistration.class)
public interface NotificationRegistration extends BridgeEntity {

    static NotificationRegistration create() {
        return new DynamoNotificationRegistration();
    }
    
    /**
     * The healthCode of the user registering their device for notifications.
     */
    String getHealthCode();
    
    /** @see #getHealthCode */
    void setHealthCode(String healthCode);

    /**
     * The GUID of this registration that is returned to the client to update the 
     * device identifier in subsequent requests. Can also be used to unregister 
     * for notifications. 
     */
    String getGuid();
    
    /** @see getGuid */
    void setGuid(String guid);
    
    /**
     * The endpointARN generated when the user's device is registered for a specific 
     * platform (using a platformARN).
     */
    String getEndpointARN();
    
    /** @see getEndpointARN */
    void setEndpointARN(String endpointARN);
    
    /**
     * The device ID submitted by the client (for reference). This is either the 
     * device token (iOS) or registrationId (Android). This value is also stored at SNS but 
     * caching it here allows us to determine when we can skip an SNS update.
     */
    String getDeviceId();
    
    /** @see getDeviceId */
    void setDeviceId(String deviceId);
    
    /**
     * The OS name for the device ID (should be one of the OperatingSystem string constants). 
     * This clarifies what kind of device identifier has been stored for this registration.
     */
    String getOsName();
    
    /** @see getPlatform */
    void setOsName(String osName);
    
    /**
     * The UTC timestamp when this record was created.
     */
    long getCreatedOn();
    
    /** @see getCreatedOn */
    void setCreatedOn(long createdOn);

    /**
     * The UTC timestamp when this record was last modified.
     */
    long getModifiedOn();
    
    /** @see getModifiedOn */
    void setModifiedOn(long modifiedOn);
}
