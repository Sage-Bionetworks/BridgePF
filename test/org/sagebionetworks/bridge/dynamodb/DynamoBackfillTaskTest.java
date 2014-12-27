package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.models.BackfillStatus;

public class DynamoBackfillTaskTest {

    @Test
    public void test() {
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        DynamoBackfillTask task = new DynamoBackfillTask("name", "user");
        assertEquals("name", task.getName());
        assertEquals("user", task.getUser());
        assertTrue(task.getTimestamp() >= timestamp);
        assertEquals(BackfillStatus.SUBMITTED.name(), task.getStatus());
    }

    @Test
    public void testId() {
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        DynamoBackfillTask task = new DynamoBackfillTask("name", "user");
        String id = task.getId();
        assertNotNull(id);
        String[] splits =id.split(":");
        assertEquals(2, splits.length);
        assertEquals("name", splits[0]);
        assertTrue(Long.parseLong(splits[1]) >= timestamp);
        task = new DynamoBackfillTask(id);
        assertEquals("name", task.getName());
        assertNull(task.getUser());
        assertTrue(task.getTimestamp() >= timestamp);
        assertNull(task.getStatus());
    }
}
