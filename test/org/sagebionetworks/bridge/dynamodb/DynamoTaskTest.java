package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class DynamoTaskTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoTask.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canRoundtripSerialize() throws Exception {
        DynamoTask task = new DynamoTask();
        task.setActivity(new Activity("Label", "task:foo"));
        task.setScheduledOn(DateTime.parse("2015-05-04T02:10:00.000-07:00"));
        task.setExpiresOn(DateTime.parse("2015-05-12T02:10:00.000-07:00"));
        task.setGuid("AAA-BBB-CCC");
        task.setSchedulePlanGuid("DDD-EEE-FFF");
        task.setStudyHealthCodeKey(new StudyIdentifierImpl("studyId"), "FFF-GGG-HHH");
        
        String output = BridgeObjectMapper.get().writeValueAsString(task);
        assertEquals("{\"guid\":\"AAA-BBB-CCC\",\"schedulePlanGuid\":\"DDD-EEE-FFF\",\"scheduledOn\":\"2015-05-04T09:10:00.000Z\",\"expiresOn\":\"2015-05-12T09:10:00.000Z\",\"activity\":{\"label\":\"Label\",\"ref\":\"task:foo\",\"activityType\":\"task\",\"type\":\"Activity\"},\"type\":\"Task\"}", output);
        
        // zero out the health code field, because that will not be serialized
        task.setStudyHealthCodeKey(null);

        DynamoTask newTask = BridgeObjectMapper.get().readValue(output, DynamoTask.class);
        assertEquals(task, newTask);
    }
    
}
