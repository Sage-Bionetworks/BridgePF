package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.apache.commons.lang3.builder.EqualsBuilder;
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
        task.setActivity(TestConstants.TEST_ACTIVITY);
        task.setLocalScheduledOn(scheduledOn);
        task.setLocalExpiresOn(expiresOn);
        task.setGuid("AAA-BBB-CCC");
        task.setHealthCode("FFF-GGG-HHH");
        task.setPersistent(true);
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String output = BridgeObjectMapper.get().writeValueAsString(task);
        
        JsonNode node = mapper.readTree(output);
        assertEquals("AAA-BBB-CCC", node.get("guid").asText());
        assertEquals(scheduledOnString, node.get("scheduledOn").asText());
        assertEquals(expiresOnString, node.get("expiresOn").asText());
        assertEquals("scheduled", node.get("status").asText());
        assertEquals("Task", node.get("type").asText());
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
    
}
