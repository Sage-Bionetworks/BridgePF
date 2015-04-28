package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;

public class DynamoTaskTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoTask.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canRoundtripSerialize() throws Exception {
        DynamoTask task = new DynamoTask();
        task.setActivity(new Activity("Label", "task:foo"));
        task.setScheduledOn(DateTime.parse("2015-05-04T02:10:00.000-07:00").getMillis());
        task.setExpiresOn(DateTime.parse("2015-05-12T02:10:00.000-07:00").getMillis());
        task.setGuid("AAA-BBB-CCC");
        task.setSchedulePlanGuid("DDD-EEE-FFF");
        task.setHealthCode("FFF-GGG-HHH");
        
        String output = BridgeObjectMapper.get().writeValueAsString(task);
        assertEquals("{\"guid\":\"AAA-BBB-CCC\",\"scheduledOn\":\"2015-05-04T09:10:00.000Z\",\"expiresOn\":\"2015-05-12T09:10:00.000Z\",\"activity\":{\"label\":\"Label\",\"ref\":\"task:foo\",\"activityType\":\"task\",\"type\":\"Activity\"},\"status\":\"scheduled\",\"type\":\"Task\"}", output);
        
        // zero out the health code field, because that will not be serialized
        task.setHealthCode(null);

        DynamoTask newTask = BridgeObjectMapper.get().readValue(output, DynamoTask.class);
        newTask.setSchedulePlanGuid(task.getSchedulePlanGuid());
        assertEquals(task, newTask);
    }
    
    @Test
    public void hasValidStatusBasedOnTimestamps() throws Exception {
        Task task = new DynamoTask();
        assertEquals(TaskStatus.AVAILABLE, task.getStatus());

        task.setScheduledOn(DateTime.now().plusHours(1).getMillis());
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        
        task.setScheduledOn(DateTime.now().minusHours(3).getMillis());
        task.setExpiresOn(DateTime.now().minusHours(1).getMillis());
        assertEquals(TaskStatus.EXPIRED, task.getStatus());
        
        task.setScheduledOn(null);
        task.setExpiresOn(null);
        
        task.setStartedOn(DateTime.now().getMillis());
        assertEquals(TaskStatus.STARTED, task.getStatus());
        
        task.setFinishedOn(DateTime.now().getMillis());
        assertEquals(TaskStatus.FINISHED, task.getStatus());
        
        task = new DynamoTask();
        task.setFinishedOn(DateTime.now().getMillis());
        assertEquals(TaskStatus.DELETED, task.getStatus());
        
        task = new DynamoTask();
        task.setScheduledOn(DateTime.now().minusHours(1).getMillis());
        task.setExpiresOn(DateTime.now().plusHours(1).getMillis());
        assertEquals(TaskStatus.AVAILABLE, task.getStatus());
    }
    
}
