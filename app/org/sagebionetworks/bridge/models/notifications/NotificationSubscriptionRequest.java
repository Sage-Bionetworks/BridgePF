package org.sagebionetworks.bridge.models.notifications;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class NotificationSubscriptionRequest {

    private final String registrationGuid;
    private final List<String> topicGuids;
    
    @JsonCreator
    public NotificationSubscriptionRequest(@JsonProperty("registrationIds") String registrationGuid,
            @JsonProperty("topicIds") List<String> topicGuids) {
        this.registrationGuid = registrationGuid;
        this.topicGuids = (topicGuids != null) ? topicGuids : ImmutableList.of(); 
    }
    
    public String getRegistrationGuid() {
        return registrationGuid;
    }

    public List<String> getTopicGuids() {
        return topicGuids;
    }
    
}
