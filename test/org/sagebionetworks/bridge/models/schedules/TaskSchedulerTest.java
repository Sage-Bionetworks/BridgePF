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
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.sagebionetworks.bridge.validators.Validate;

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
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(NOW.plusWeeks(1))
            .withEvents(empty).build();
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(NOW.plusWeeks(1)).build();
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

        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusWeeks(1)));
        DynamoTask task = (DynamoTask)tasks.get(0);

        assertNotNull(task.getGuid());
        assertEquals("Label", task.getActivity().getLabel());
        assertEquals("tapTest", task.getActivity().getRef());
        assertNotNull(task.getScheduledOn());
        assertNotNull(task.getExpiresOn());
        assertNotNull(task.getHealthCode());
        assertNotNull(task.getTimeZone());
        assertNotNull(task.getRunKey());
        assertNotNull(task.getTimeZone());
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

        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusMonths(2)));
        assertEquals(asLong("2015-04-23 10:00"), tasks.get(0).getScheduledOn().getMillis());

        tasks = schedule2.getScheduler().getTasks(getContext(NOW.plusMonths(2)));
        assertEquals(0, tasks.size());
        
        DateTime TASK1_EVENT = asDT("2015-04-25 15:32");
        
        // Now say that task was finished a couple of days after that:
        events.put("task:task1", TASK1_EVENT);
        
        tasks = schedule2.getScheduler().getTasks(getContext(NOW.plusMonths(2)));
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
        tasks = schedule.getScheduler().getTasks(getContext(zone, NOW.plusMonths(1)));
        assertEquals(DateTime.parse("2015-03-27T07:00:00.000-07:00"), tasks.get(0).getScheduledOn());
        
        // Add an endsOn value in GMT, it shouldn't matter, it'll prevent event from firing
        schedule.setEndsOn("2015-03-25T13:00:00.000Z"); // one hour before the event
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusMonths(1)));
        assertEquals(0, tasks.size());
    }
    
    @Test
    public void taskSequencesGeneratedAtDifferentTimesAreTheSame() throws Exception {
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setInterval("P1D");
        schedule.addTimes("10:00");
        
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(20)));
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-13 08:00"));
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(20)));
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void cronTasksGeneratedAtDifferentTimesAreTheSame() throws Exception {
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setCronTrigger("0 0 10 ? * MON,TUE,WED,THU,FRI,SAT,SUN *");

        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(20)));
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-07 08:00"));
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(20)));
        assertDates(tasks, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void twoTasksWithSameTimestampCanBeDifferentiated() throws Exception {
        // Add second activity to same schedule
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity.Builder().withLabel("Label1").withTask("tapTest").build());
        schedule.getActivities().add(new Activity.Builder().withLabel("Label2").withTask("tapTest").build());
        schedule.setScheduleType(ONCE);

        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(7)));
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
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        assertEquals(NOW.minusHours(3), tasks.get(0).getScheduledOn());

        events.put("scheduledOn:task:foo", NOW.plusHours(8));
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        assertEquals(NOW.plusHours(8), tasks.get(0).getScheduledOn());
    }
    
    @Test
    public void willSelectFirstEventIdWithARecord() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setEventId("survey:event, enrollment");
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ONCE);
        
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        assertEquals(ENROLLMENT.plusDays(2), tasks.get(0).getScheduledOn());
        
        events.remove("survey:event");
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        assertEquals(ENROLLMENT, tasks.get(0).getScheduledOn());
        
        // BUT this produces nothing because the system doesn't fallback to enrollment if an event has been set
        schedule.setEventId("survey:event");
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        assertEquals(0, tasks.size());
    }
    
    @Test
    public void tasksMarkedPersistentUnderCorrectCircumstances() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("task:foo:finished,enrollment");
        schedule.addActivity(new Activity.Builder().withLabel("Foo").withTask("foo").build());
        schedule.addActivity(new Activity.Builder().withLabel("Bar").withTask("bar").build());
        
        tasks = schedule.getScheduler().getTasks(getContext(NOW.plusDays(1)));
        Task task1 = tasks.get(0);
        assertEquals("Foo", task1.getActivity().getLabel());
        assertTrue(task1.getPersistent());
        assertTrue(task1.getActivity().isPersistentlyRescheduledBy(schedule));
        
        Task task2 = tasks.get(1);
        assertEquals("Bar", task2.getActivity().getLabel());
        assertFalse(task2.getPersistent());
        assertFalse(task2.getActivity().isPersistentlyRescheduledBy(schedule));
    }
    
    
    @Test
    public void scheduleIsTranslatedToTimeZoneWhenCreatedAndPersisted() {
        // 10am every morning from the time of enrollment
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withLabel("Foo").withTask("foo").build());
        Validate.entityThrowingException(new ScheduleValidator(), schedule);
        
        // User is in Moscow, however.
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        List<Task> tasks = schedule.getScheduler().getTasks(getContext(zone, DateTime.now().plusDays(1)));
        assertEquals("2015-04-06T10:00:00.000+03:00", tasks.get(0).getScheduledOn().toString());
        assertEquals("2015-04-07T10:00:00.000+03:00", tasks.get(1).getScheduledOn().toString());
        
        // Now the user flies across the planet, and retrieves the tasks again, they are in the new timezone
        zone = DateTimeZone.forOffsetHours(-7);
        tasks = schedule.getScheduler().getTasks(getContext(zone, DateTime.now().plusDays(1)));
        assertEquals("2015-04-06T10:00:00.000-07:00", tasks.get(0).getScheduledOn().toString());
        assertEquals("2015-04-07T10:00:00.000-07:00", tasks.get(1).getScheduledOn().toString());
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return getContext(DateTimeZone.UTC, endsOn);
    }

    private ScheduleContext getContext(DateTimeZone zone, DateTime endsOn) {
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY)
            .withTimeZone(zone).withEndsOn(endsOn).withHealthCode("AAA").withSchedulePlanGuid("BBB").withEvents(events).build();
    }
    
}
