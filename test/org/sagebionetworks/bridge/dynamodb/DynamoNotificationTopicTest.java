package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationTopicTest {

    @Test
    public void canSerialize() throws Exception {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("ABC");
        topic.setName("My Test Topic");
        topic.setStudyId("test-study");
        topic.setTopicARN("aTopicARN");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(topic);
        assertEquals("ABC", node.get("guid").asText());
        assertEquals("My Test Topic", node.get("name").asText());
        assertEquals("NotificationTopic", node.get("type").asText());
        assertNull(node.get("studyId").asText());
        assertNull(node.get("topicARN"));
        
        // The values that are not serialized are provided by the service, they aren't
        // settable by the API caller.
        NotificationTopic deser = BridgeObjectMapper.get().readValue(node.toString(), NotificationTopic.class);
        assertEquals("ABC", deser.getGuid());
        assertEquals("My Test Topic", deser.getName());
        assertNull("test-study", deser.getStudyId());
        assertNull(deser.getTopicARN());
    }
}
