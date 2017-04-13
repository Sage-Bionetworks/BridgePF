package org.sagebionetworks.bridge.models.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple value object for communicating the status of a subscription back to the client. The 
 * client will get back one record for every topic in the study.
 */
public class SubscriptionStatus {

    private final String topicGuid;
    private final String topicName;
    private final boolean subscribed;

    public SubscriptionStatus(@JsonProperty("topicGuid") String topicGuid, 
            @JsonProperty("topicName") String topicName,
            @JsonProperty("subscribed") boolean subscribed) {
        this.topicGuid = topicGuid;
        this.topicName = topicName;
        this.subscribed = subscribed;
    }
    
    public String getTopicGuid() {
        return topicGuid;
    }
    public String getTopicName() {
        return topicName;
    }
    public boolean isSubscribed() {
        return subscribed;
    }
}
