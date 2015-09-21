package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asLong;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;

import com.newrelic.agent.deps.com.google.common.collect.Maps;

/**
 * These tests cover other aspects of the scheduler besides the accuracy of its scheduling.
 *
 */
public class TaskSchedulerTest {

    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    private static final DateTime NOW = DateTime.parse("2015-03-26T14:40:00Z");
    
    private List<Task> tasks;
    private Map<String,DateTime> events;
    
    @Before
    public void before() {
        // Day of tests is 2015-04-06T10:10:10.000-07:00 for purpose of calculating expiration
        DateTimeUtils.setCurrentMillisFixed(1428340210000L);

        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
        events.put("survey:event", ENROLLMENT.plusDays(2));
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void schedulerIsPassedNoEvents() {
        // Shouldn't happen, but then again, the events table starts empty.
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1D");
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        
        Map<String,DateTime> empty = Maps.newHashMap();
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusWeeks(1), empty, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        context = new ScheduleContext(DateTimeZone.UTC, NOW.plusWeeks(1), null, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void taskIsComplete() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setLabel("This is a label");
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setExpires("P3Y");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusWeeks(1), events, "schedulePlanGuid");
        tasks = schedule.getScheduler().getTasks(context);
        Task task = tasks.get(0);

        assertNotNull(task.getGuid());
        assertEquals("Label", task.getActivity().getLabel());
        assertEquals("tapTest", task.getActivity().getRef());
        assertNotNull(task.getScheduledOn());
        assertNotNull(task.getExpiresOn());
    }
    
    /**
     * Task #1 starts from enrollment. Every time it is scheduled, schedule Task#2 to 
     * happen once, at exactly the same time. This is not useful, but it should work, 
     * or something is wrong with our model vis-a-vis the implementation.
     */
    @Test
    public void tasksCanBeChainedTogether() throws Exception {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1M");
        
        Schedule schedule2 = new Schedule();
        schedule2.getActivities().add(TestConstants.TEST_ACTIVITY);
        schedule2.setScheduleType(ONCE);
        schedule2.setEventId("task:task1");

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusMonths(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(asLong("2015-04-23 10:00"), tasks.get(0).getScheduledOn().getMillis());

        tasks = schedule2.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        DateTime TASK1_EVENT = asDT("2015-04-25 15:32");
        
        // Now say that task was finished a couple of days after that:
        events.put("task:task1", TASK1_EVENT);
        
        context = new ScheduleContext(DateTimeZone.UTC, NOW.plusMonths(2), events, null);
        tasks = schedule2.getScheduler().getTasks(context);
        assertEquals(TASK1_EVENT, tasks.get(0).getScheduledOn());
    }
    
    @Test
    public void taskSchedulerWorksInDifferentTimezone() {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setEventId("foo");
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay("P2D");
        schedule.addTimes("07:00");

        // Event is recorded in PDT. And when we get the task back, it is scheduled in PDT. 
        events.put("foo", DateTime.parse("2015-03-25T07:00:00.000-07:00"));
        DateTimeZone zone = DateTimeZone.forOffsetHours(-7);
        ScheduleContext context = new ScheduleContext(zone, NOW.plusMonths(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(DateTime.parse("2015-03-27T07:00:00.000-07:00"), tasks.get(0).getScheduledOn());
        
        // Add an endsOn value in GMT, it shouldn't matter, it'll prevent event from firing
        schedule.setEndsOn("2015-03-25T13:00:00.000Z"); // one hour before the event
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    
    @Test
    public void taskSequencesGeneratedAtDifferentTimesAreTheSame() throws Exception {
        DateTime until = NOW.plusDays(20);
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setInterval("P1D");
        schedule.addTimes("10:00");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, until, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-13 08:00"));
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void cronTasksGeneratedAtDifferentTimesAreTheSame() throws Exception {
        DateTime until = NOW.plusDays(20);
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setCronTrigger("0 0 10 ? * MON,TUE,WED,THU,FRI,SAT,SUN *");

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, until, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-07 08:00"));
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void twoTasksWithSameTimestampCanBeDifferentiated() throws Exception {
        // Add second activity to same schedule
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity.Builder().withLabel("Label1").withTask("tapTest").build());
        schedule.getActivities().add(new Activity.Builder().withLabel("Label2").withTask("tapTest").build());
        schedule.setScheduleType(ONCE);

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(7), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(2, tasks.size());
        assertNotEquals(tasks.get(0), tasks.get(1));
    }
    
    /**
     * You can submit tasks that weren't derived from any scheduling information. Details TBD, but the 
     * scheduler needs to support a "submit task, get tasks, see task under new GUID" scenario.
     */
    @Test
    public void taskThatIsAlwaysAvailable() throws Exception {
        // Just fire this each time it is itself completed, and it never expires.
        Schedule schedule = new Schedule();
        schedule.setEventId("scheduledOn:task:foo");
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ONCE);
        
        events.put("scheduledOn:task:foo", NOW.minusHours(3));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(NOW.minusHours(3), tasks.get(0).getScheduledOn());

        events.put("scheduledOn:task:foo", NOW.plusHours(8));
        context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(NOW.plusHours(8), tasks.get(0).getScheduledOn());
    }
    
    @Test
    public void willSelectFirstEventIdWithARecord() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setEventId("survey:event, enrollment");
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ONCE);
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(ENROLLMENT.plusDays(2), tasks.get(0).getScheduledOn());
        
        events.remove("survey:event");
        context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(ENROLLMENT, tasks.get(0).getScheduledOn());
        
        // BUT this produces nothing because the system doesn't fallback to enrollment if an event has been set
        schedule.setEventId("survey:event");
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    
    @Test
    public void tasksMarkedPersistentUnderCorrectCircumstances() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("task:foo:finished,enrollment");
        schedule.addActivity(new Activity.Builder().withLabel("Foo").withTask("foo").build());
        schedule.addActivity(new Activity.Builder().withLabel("Bar").withTask("bar").build());
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, NOW.plusDays(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        Task task1 = tasks.get(0);
        assertEquals("Foo", task1.getActivity().getLabel());
        assertTrue(task1.getPersistent());
        assertTrue(task1.getActivity().isPersistentlyRescheduledBy(schedule));
        
        Task task2 = tasks.get(1);
        assertEquals("Bar", task2.getActivity().getLabel());
        assertFalse(task2.getPersistent());
        assertFalse(task2.getActivity().isPersistentlyRescheduledBy(schedule));
    }
    
}
