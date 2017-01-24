package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationTopicTest {

    private static final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC);
    private static final long TIMESTAMP = DATE_TIME.getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("ABC");
        topic.setName("My Test Topic");
        topic.setStudyId("test-study");
        topic.setTopicARN("aTopicARN");
        topic.setDescription("A description.");
        topic.setCreatedOn(TIMESTAMP);
        topic.setModifiedOn(TIMESTAMP);

        JsonNode node = BridgeObjectMapper.get().valueToTree(topic);
        assertEquals("ABC", node.get("guid").asText());
        assertEquals("My Test Topic", node.get("name").asText());
        assertEquals("NotificationTopic", node.get("type").asText());
        assertEquals("A description.", node.get("description").asText());
        assertEquals(DATE_TIME.toString(), node.get("createdOn").asText());
        assertEquals(DATE_TIME.toString(), node.get("modifiedOn").asText());
        assertNull(node.get("studyId"));
        assertNull(node.get("topicARN"));
        
        // The values that are not serialized are provided by the service, they aren't
        // settable by the API caller.
        NotificationTopic deser = BridgeObjectMapper.get().readValue(node.toString(), NotificationTopic.class);
        assertEquals("ABC", deser.getGuid());
        assertEquals("My Test Topic", deser.getName());
        assertNull("test-study", deser.getStudyId());
        assertEquals("A description.", deser.getDescription());
        assertEquals(TIMESTAMP, deser.getCreatedOn());
        assertEquals(TIMESTAMP, deser.getModifiedOn());
        assertNull(deser.getTopicARN());
    }
}
