package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoTaskTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoTask.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canRoundtripSerialize() throws Exception {
        LocalDateTime scheduledOn = LocalDateTime.now().plusWeeks(1);
        LocalDateTime expiresOn = LocalDateTime.now().plusWeeks(1);
        
        String scheduledOnString = scheduledOn.toDateTime(DateTimeZone.UTC).toString();
        String expiresOnString = expiresOn.toDateTime(DateTimeZone.UTC).toString();
        
        DynamoTask task = new DynamoTask();
        task.setTimeZone(DateTimeZone.UTC);
        task.setActivity(TestConstants.TEST_3_ACTIVITY);
        task.setLocalScheduledOn(scheduledOn);
        task.setLocalExpiresOn(expiresOn);
        task.setGuid("AAA-BBB-CCC");
        task.setHealthCode("FFF-GGG-HHH");
        task.setPersistent(true);
        task.setMinAppVersion(1);
        task.setMaxAppVersion(3);
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String output = BridgeObjectMapper.get().writeValueAsString(task);
        
        JsonNode node = mapper.readTree(output);
        assertEquals("AAA-BBB-CCC", node.get("guid").asText());
        assertEquals(scheduledOnString, node.get("scheduledOn").asText());
        assertEquals(expiresOnString, node.get("expiresOn").asText());
        assertEquals("scheduled", node.get("status").asText());
        assertEquals("Task", node.get("type").asText());
        assertEquals(1, node.get("minAppVersion").asInt());
        assertEquals(3, node.get("maxAppVersion").asInt());
        assertTrue(node.get("persistent").asBoolean());
        
        JsonNode activityNode = node.get("activity");
        assertEquals("Label", activityNode.get("label").asText());
        assertEquals("tapTest", activityNode.get("ref").asText());
        assertEquals("task", activityNode.get("activityType").asText());
        assertEquals("Activity", activityNode.get("type").asText());
        
        // zero out the health code field, because that will not be serialized
        task.setHealthCode(null);
        
        DynamoTask newTask = mapper.readValue(output, DynamoTask.class);
        // The local schedule values are not serialized and the calculated values aren't deserialized, 
        // but they are verified above.
        newTask.setTimeZone(DateTimeZone.UTC);
        newTask.setLocalScheduledOn(scheduledOn);
        newTask.setLocalExpiresOn(expiresOn);
        
        // Also works without having to reset the timezone.
        //EqualsBuilder.reflectionEquals(task, newTask, "localScheduledOn", "localExpiresOn", "scheduledOn", "expiresOn");
        assertEquals(task, newTask);
    }
    
    @Test
    public void hasValidStatusBasedOnTimestamps() throws Exception {
        LocalDateTime now = LocalDateTime.now(DateTimeZone.UTC);
        
        DynamoTask task = new DynamoTask();
        task.setTimeZone(DateTimeZone.UTC);
        
        assertEquals(TaskStatus.AVAILABLE, task.getStatus());

        task.setLocalScheduledOn(now.plusHours(1));
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        
        task.setLocalScheduledOn(now.minusHours(3));
        task.setLocalExpiresOn(now.minusHours(1));
        assertEquals(TaskStatus.EXPIRED, task.getStatus());
        
        task.setLocalScheduledOn(null);
        task.setLocalExpiresOn(null);
        
        task.setStartedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(TaskStatus.STARTED, task.getStatus());
        
        task.setFinishedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(TaskStatus.FINISHED, task.getStatus());
        
        task = new DynamoTask();
        task.setFinishedOn(DateTime.now().getMillis());
        assertEquals(TaskStatus.DELETED, task.getStatus());
        
        task = new DynamoTask();
        task.setLocalScheduledOn(now.minusHours(1));
        task.setLocalExpiresOn(now.plusHours(1));
        assertEquals(TaskStatus.AVAILABLE, task.getStatus());
    }
    
    /**
     * If a timestamp is not derived from a DateTime value passed into DynamoTask, or set after construction, 
     * then the DateTime scheduledOn and expiresOn values are null.
     */
    @Test
    public void dealsTimeZoneAppropriately() {
        DateTime dateTime = DateTime.parse("2010-10-15T00:00:00.001+06:00");
        DateTime dateTimeInZone = DateTime.parse("2010-10-15T00:00:00.001Z");
        
        // Task with datetime and zone (which is different)
        DynamoTask task = new DynamoTask();
        // Without a time zone, getStatus() works
        assertEquals(TaskStatus.AVAILABLE, task.getStatus());
        // Now set some values
        task.setScheduledOn(dateTime);
        task.setTimeZone(DateTimeZone.UTC);
        
        // Scheduled time should be in the time zone that is set
        assertEquals(DateTimeZone.UTC, task.getScheduledOn().getZone());
        // But the datetime does not itself change (this is one way to test this)
        assertEquals(dateTimeInZone.toLocalDateTime(), task.getScheduledOn().toLocalDateTime());
        
        // setting new time zone everything shifts only in zone, not date or time
        DateTimeZone newZone = DateTimeZone.forOffsetHours(3);
        task.setTimeZone(newZone);
        LocalDateTime copy = task.getScheduledOn().toLocalDateTime();
        assertEquals(newZone, task.getScheduledOn().getZone());
        assertEquals(dateTimeInZone.toLocalDateTime(), copy);
    }
    
    @Test
    public void dateTimesConvertedTest() {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(3);
        DateTime now = DateTime.now();
        DateTime then = DateTime.now().minusDays(1);
        
        DynamoTask task = new DynamoTask();
        task.setTimeZone(timeZone);
        task.setScheduledOn(now);
        task.setExpiresOn(then);
        assertEquals(task.getLocalScheduledOn(), now.toLocalDateTime());
        assertEquals(task.getLocalExpiresOn(), then.toLocalDateTime());
        
        LocalDateTime local1 = LocalDateTime.parse("2010-01-01T10:10:10");
        LocalDateTime local2 = LocalDateTime.parse("2010-02-02T10:10:10");
        
        task = new DynamoTask();
        task.setTimeZone(timeZone);
        task.setLocalScheduledOn(local1);
        task.setLocalExpiresOn(local2);
        assertEquals(task.getScheduledOn(), local1.toDateTime(timeZone));
        assertEquals(task.getExpiresOn(), local2.toDateTime(timeZone));
    }
    
}
