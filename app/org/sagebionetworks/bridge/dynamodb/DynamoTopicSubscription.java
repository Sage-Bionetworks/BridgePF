package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "NotificationTopicSubscription")
public class DynamoTopicSubscription implements TopicSubscription {

    private String registrationGuid;
    private String topicGuid;
    private String subscriptionARN;

    @DynamoDBHashKey
    @Override
    public String getRegistrationGuid() {
        return registrationGuid;
    }
    @Override
    public void setRegistrationGuid(String registrationGuid) {
        this.registrationGuid = registrationGuid;
    }
    @DynamoDBRangeKey
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
