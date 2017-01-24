package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.notifications.NotificationTopicSubscription;

public class DynamoNotificationTopicSubscription implements NotificationTopicSubscription {

    private String healthCode;
    private String registrationGuid;
    private String topicGuid;
    private String subscriptionARN;

    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @Override
    public String getRegistrationGuid() {
        return registrationGuid;
    }
    @Override
    public void setRegistrationGuid(String registrationGuid) {
        this.registrationGuid = registrationGuid;
    }
    @Override
    public String getTopicGuid() {
        return topicGuid;
    }
    @Override
    public void setTopicGuid(String topicGuid) {
        this.topicGuid = topicGuid;
    }
    @Override
    public String getSubscriptionARN() {
        return subscriptionARN;
    }
    @Override
    public void setSubscriptionARN(String subscriptionARN) {
        this.subscriptionARN = subscriptionARN;
    }

}
