package org.sagebionetworks.bridge.models.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;

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
        ActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode("BBB").withObjectType(ActivityEventObjectType.ENROLLMENT)
                        .withTimestamp(DateTime.now()).build();
        
        assertEquals("enrollment", event.getEventId());
    }
   
}
