package org.sagebionetworks.bridge.models.notifications;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class SubscriptionRequestTest {

    @Test
    public void canSerialize() throws Exception {
        Set<String> topicGuids = Sets.newHashSet("topicA", "topicB");
        
        SubscriptionRequest request = new SubscriptionRequest("registrationGuid", topicGuids);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(request);
        Set<String> serializedTopicGuids = Sets.newHashSet(node.get("topicGuids").get(0).asText(), node.get("topicGuids").get(1).asText());
        
        assertEquals("registrationGuid", request.getRegistrationGuid());
        assertEquals(topicGuids, serializedTopicGuids);
        assertEquals("SubscriptionRequest", node.get("type").asText());
        
        SubscriptionRequest deser = BridgeObjectMapper.get().readValue(node.toString(), SubscriptionRequest.class);
        assertEquals("registrationGuid", deser.getRegistrationGuid());
        assertEquals(topicGuids, deser.getTopicGuids());
    }
}
