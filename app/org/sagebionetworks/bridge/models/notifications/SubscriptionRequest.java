package org.sagebionetworks.bridge.models.notifications;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;

public class SubscriptionRequest {

    private Set<String> topicGuids;
    
    @JsonCreator
    public SubscriptionRequest(@JsonProperty("topicGuids") Set<String> topicGuids) {
        this.topicGuids = (topicGuids != null) ? topicGuids : ImmutableSet.of(); 
    }
    
    public Set<String> getTopicGuids() {
        return topicGuids;
    }
    
}
