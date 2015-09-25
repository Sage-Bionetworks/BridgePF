package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

import com.google.common.collect.Lists;

public class TaskTest {

    @Test
    public void testComparator() {
        DynamoTask task1 = new DynamoTask();
        task1.setTimeZone(DateTimeZone.UTC);
        task1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        task1.setActivity(TestConstants.TEST_ACTIVITY);
        
        // Definitely later
        DynamoTask task2 = new DynamoTask();
        task2.setTimeZone(DateTimeZone.UTC);
        task2.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        task2.setActivity(TestConstants.TEST_ACTIVITY);
        
        // The same as 2 in all respects but activity label comes earlier in alphabet
        DynamoTask task3 = new DynamoTask();
        task3.setTimeZone(DateTimeZone.UTC);
        task3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        task3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<Task> tasks = Lists.newArrayList(task1, task2, task3);
        Collections.sort(tasks, Task.TASK_COMPARATOR);
        
        // Most recent sorted alphabetically, then the older one.
        assertEquals(task2, tasks.get(0));
        assertEquals(task3, tasks.get(1));
        assertEquals(task1, tasks.get(2));
    }
    
    @Test
    public void handlesNullFieldsReasonably() {
        // No time zone
        DynamoTask task1 = new DynamoTask();
        task1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        task1.setActivity(TestConstants.TEST_ACTIVITY);
        
        // scheduledOn
        DynamoTask task2 = new DynamoTask();
        task2.setTimeZone(DateTimeZone.UTC);
        task2.setActivity(TestConstants.TEST_ACTIVITY);
        
        // This one is okay
        DynamoTask task3 = new DynamoTask();
        task3.setTimeZone(DateTimeZone.UTC);
        task3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        task3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<Task> tasks = Lists.newArrayList(task1, task2, task3);
        Collections.sort(tasks, Task.TASK_COMPARATOR);
        
        // Task 3 comes first because it's complete, the others follow. This is arbitrary...
        // in reality they are broken tasks, but the comparator will not fail.
        assertEquals(task3, tasks.get(0));
        assertEquals(task1, tasks.get(1));
        assertEquals(task2, tasks.get(2));
    }
    
}
