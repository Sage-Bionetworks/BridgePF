package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationTopicTest {

    private static final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC);
    private static final long TIMESTAMP = DATE_TIME.getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        // Create POJO.
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("ABC");
        topic.setName("My Test Topic");
        topic.setShortName("Test Topic");
        topic.setStudyId("test-study");
        topic.setTopicARN("aTopicARN");
        topic.setDescription("A description.");
        topic.setCreatedOn(TIMESTAMP);
        topic.setModifiedOn(TIMESTAMP);
        topic.setDeleted(true);

        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(ImmutableSet.of("group1", "group2"));
        topic.setCriteria(criteria);

        // Serialize to JSON.
        JsonNode node = BridgeObjectMapper.get().valueToTree(topic);
        assertEquals("ABC", node.get("guid").textValue());
        assertEquals("My Test Topic", node.get("name").textValue());
        assertEquals("Test Topic", node.get("shortName").textValue());
        assertEquals("NotificationTopic", node.get("type").textValue());
        assertEquals("A description.", node.get("description").textValue());
        assertEquals(DATE_TIME.toString(), node.get("createdOn").textValue());
        assertEquals(DATE_TIME.toString(), node.get("modifiedOn").textValue());
        assertNull(node.get("studyId"));
        assertNull(node.get("topicARN"));
        assertTrue(node.get("deleted").booleanValue());

        JsonNode criteriaNode = node.get("criteria");
        JsonNode allOfGroupsNode = criteriaNode.get("allOfGroups");
        assertEquals(2, allOfGroupsNode.size());

        Set<String> allOfGroupsJsonSet = new HashSet<>();
        for (JsonNode oneAllOfGroupNode : allOfGroupsNode) {
            allOfGroupsJsonSet.add(oneAllOfGroupNode.textValue());
        }
        assertEquals(criteria.getAllOfGroups(), allOfGroupsJsonSet);

        // De-serialize back to POJO.
        // The values that are not serialized are provided by the service, they aren't
        // settable by the API caller.
        NotificationTopic deser = BridgeObjectMapper.get().readValue(node.toString(), NotificationTopic.class);
        assertEquals("ABC", deser.getGuid());
        assertEquals("My Test Topic", deser.getName());
        assertEquals("Test Topic", deser.getShortName());
        assertNull("test-study", deser.getStudyId());
        assertEquals("A description.", deser.getDescription());
        assertEquals(TIMESTAMP, deser.getCreatedOn());
        assertEquals(TIMESTAMP, deser.getModifiedOn());
        assertNull(deser.getTopicARN());
        assertEquals(criteria, deser.getCriteria());
        assertTrue(deser.isDeleted());
    }
}
