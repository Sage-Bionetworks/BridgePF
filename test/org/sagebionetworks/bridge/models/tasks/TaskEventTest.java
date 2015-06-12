package org.sagebionetworks.bridge.models.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent.Builder;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

public class TaskEventTest {

    @Test
    public void cannotConstructBadTaskEvent() {
        try {
            new DynamoTaskEvent.Builder().build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withHealthCode("BBB").build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withObjectType(TaskEventObjectType.QUESTION).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withHealthCode("BBB").withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("eventId cannot be null (may be missing object or event type)", e.getErrors().get("eventId").get(0));
        }
        
    }
    
    @Test
    public void canConstructSimpleEventId() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoTaskEvent.Builder();
        TaskEvent event = builder.withObjectType(TaskEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void canConstructEventNoAction() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoTaskEvent.Builder();
        TaskEvent event = builder.withObjectType(TaskEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, new DateTime(event.getTimestamp()));
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void simpleTaskEventIdIsCorrect() {
        TaskEvent event = new DynamoTaskEvent.Builder().withHealthCode("BBB").withObjectType(TaskEventObjectType.ENROLLMENT)
                        .withTimestamp(DateTime.now()).build();
        
        assertEquals("enrollment", event.getEventId());
    }
   
}
