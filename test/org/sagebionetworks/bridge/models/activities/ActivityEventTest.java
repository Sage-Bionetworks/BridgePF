package org.sagebionetworks.bridge.models.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

public class ActivityEventTest {

    @Test
    public void cannotConstructBadActivityEvent() {
        try {
            new DynamoActivityEvent.Builder().build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoActivityEvent.Builder().withHealthCode("BBB").build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
        }
        try {
            new DynamoActivityEvent.Builder().withObjectType(ActivityEventObjectType.QUESTION).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoActivityEvent.Builder().withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoActivityEvent.Builder().withHealthCode("BBB").withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("eventId cannot be null (may be missing object or event type)", e.getErrors().get("eventId").get(0));
        }
        
    }
    
    @Test
    public void canConstructSimpleEventId() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ActivityEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void canConstructEventNoAction() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ActivityEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void simpleActivityEventIdIsCorrect() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode("BBB")
                .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void activitiesRetrievedEvent() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withObjectType(ActivityEventObjectType.ACTIVITIES_RETRIEVED).withHealthCode("BBB").withTimestamp(now)
                .build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("activities_retrieved", event.getEventId());
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"healthCode\":\"test-health-code\",\n" +
                "   \"eventId\":\"test-event\",\n" +
                "   \"answerValue\":\"dummy answer\",\n" +
                "   \"timestamp\":\"2018-08-20T16:15:19.913Z\"\n" +
                "}";

        // Convert to POJO.
        ActivityEvent activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertEquals("test-health-code", activityEvent.getHealthCode());
        assertEquals("test-event", activityEvent.getEventId());
        assertEquals("dummy answer", activityEvent.getAnswerValue());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-08-20T16:15:19.913Z"), activityEvent.getTimestamp()
                .longValue());

        // Convert back to JSON.
        JsonNode activityNode = BridgeObjectMapper.get().convertValue(activityEvent, JsonNode.class);
        assertEquals("test-health-code", activityNode.get("healthCode").textValue());
        assertEquals("test-event", activityNode.get("eventId").textValue());
        assertEquals("dummy answer", activityNode.get("answerValue").textValue());
        assertEquals("2018-08-20T16:15:19.913Z", activityNode.get("timestamp").textValue());
        assertEquals("ActivityEvent", activityNode.get("type").textValue());

        // Test activity event writer, which filters out health code.
        String filteredJsonText = ActivityEvent.ACTIVITY_EVENT_WRITER.writeValueAsString(activityEvent);
        JsonNode filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
        assertEquals("test-event", filteredActivityNode.get("eventId").textValue());
        assertEquals("dummy answer", filteredActivityNode.get("answerValue").textValue());
        assertEquals("2018-08-20T16:15:19.913Z", filteredActivityNode.get("timestamp").textValue());
        assertEquals("ActivityEvent", filteredActivityNode.get("type").textValue());
    }
}
