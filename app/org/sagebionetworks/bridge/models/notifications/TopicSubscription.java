package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.dynamodb.DynamoTopicSubscription;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A record of a subscription of a user's device, registered to receive notifications, with one 
 * of the topics in the study. This registration has also been made in SNS so that this user will 
 * receive any messages sent to the Bridge topic's peer SNS topic.
 * 
 * Right now subscriptions should match 1:1 with SNS topic subscriptions. Once we introduce the 
 * filtering of messages by Bridge Criteria, this record will indicate the desire of a user to 
 * receive notifications for a topic, but they will only be registered with the SNS topic if 
 * they also match the Criteria. In other words, this subscription will only note their preference 
 * to subscribe or not, separate from whether they are subscribed with SNS.
 */
@BridgeTypeName("TopicSubscription")
@JsonDeserialize(as=DynamoTopicSubscription.class)
public interface TopicSubscription extends BridgeEntity {

    static TopicSubscription create() {
        return new DynamoTopicSubscription();
    }
    
    String getRegistrationGuid();
    void setRegistrationGuid(String registrationGuid);
    
    String getTopicGuid();
    void setTopicGuid(String topicGuid);
    
    String getSubscriptionARN();
    void setSubscriptionARN(String subscriptionARN);
    
}
