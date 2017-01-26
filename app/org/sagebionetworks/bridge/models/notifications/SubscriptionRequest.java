package org.sagebionetworks.bridge.models.notifications;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;

public class SubscriptionRequest {

    private final String registrationGuid;
    private final Set<String> topicGuids;
    
    @JsonCreator
    public SubscriptionRequest(@JsonProperty("registrationGuid") String registrationGuid,
            @JsonProperty("topicGuids") Set<String> topicGuids) {
        this.registrationGuid = registrationGuid;
        this.topicGuids = (topicGuids != null) ? topicGuids : ImmutableSet.of(); 
    }
    
    public String getRegistrationGuid() {
        return registrationGuid;
    }

    public Set<String> getTopicGuids() {
        return topicGuids;
    }
    
}
