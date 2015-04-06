package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import com.newrelic.agent.deps.com.google.common.collect.Maps;

/**
 * These tests cover other aspects of the scheduler besides the accuracy of its scheduling.
 *
 */
public class TaskSchedulerTest {

    private static final DateTime ENROLLMENT_TIMESTAMP = DateTime.parse("2015-03-23T10:00:00Z");
    private static final DateTime NOW_TIMESTAMP = DateTime.parse("2015-03-26T14:40:00Z");
    private static final DateTime MEDICATION_TIMESTAMP = DateTime.parse("2015-04-02T13:42:00Z");
    
    private Map<String,DateTime> events;
    
    @Before
    public void before() {
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT_TIMESTAMP);
        // Now is April 2nd, 2015 @ 2:40pm, GST
        events.put("now", NOW_TIMESTAMP);
        // Last took medication on April 2nd, 2015 @ 1:42pm GST
        events.put("medication", MEDICATION_TIMESTAMP);
    }
    
    @Test
    public void taskIsComplete() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setLabel("This is a label");
        schedule.addActivity(new Activity("activity label", "ref:task"));
        schedule.setExpires("P3D");
        List<Task> tasks = SchedulerFactory.getScheduler("schedulePlanGuid", schedule).getTasks(events, NOW_TIMESTAMP.plusWeeks(1));
        
        Task task = tasks.get(0);
        
        assertEquals("schedulePlanGuid", task.getSchedulePlanGuid());
        assertNotNull(task.getGuid());
        assertEquals("activity label", task.getActivity().getLabel());
        assertEquals("ref:task", task.getActivity().getRef());
        assertNotNull(task.getStartsOn());
        assertNotNull(task.getEndsOn());
    }
    
    
    /**
     * Task #1 starts from enrollment. Every time it is scheduled, schedule Task#2 to 
     * happen once, at exactly the same time. This is not useful, but it should work, 
     * or something is wrong with our model vis-a-vis the implementation.
     */
    @Test
    public void tasksCanBeChainedTogether() throws Exception {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity("Task #1", "task1"));
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1M");
        
        Schedule schedule2 = new Schedule();
        schedule2.getActivities().add(new Activity("Task #2", "task2"));
        schedule2.setScheduleType(ONCE);
        schedule2.setEventId("task:task1");

        List<Task> tasks = SchedulerFactory.getScheduler("", schedule).getTasks(events, NOW_TIMESTAMP.plusMonths(2));
        assertEquals(dt("2015-04-23 10:00"), tasks.get(0).getStartsOn());

        tasks = SchedulerFactory.getScheduler("", schedule2).getTasks(events, NOW_TIMESTAMP.plusMonths(2));
        assertEquals(0, tasks.size());
        
        DateTime TASK1_EVENT = dt("2015-04-25 15:32");
        
        // Now say that task was finished a couple of days after that:
        events.put("task:task1", TASK1_EVENT);
        
        tasks = SchedulerFactory.getScheduler("", schedule2).getTasks(events, NOW_TIMESTAMP.plusMonths(2));
        assertEquals(TASK1_EVENT, tasks.get(0).getStartsOn());
    }
    
    private DateTime dt(String string) {
        return DateTime.parse(string.replace(" ", "T") + ":00Z");
    }
    
}
