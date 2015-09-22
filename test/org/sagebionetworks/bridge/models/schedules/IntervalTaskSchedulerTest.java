package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asLong;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;

import com.google.common.collect.Maps;

public class IntervalTaskSchedulerTest {

    private Map<String, DateTime> events;
    private List<Task> tasks;
    
    private DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    @Before
    public void before() {
        // Day of tests is 2015-04-06T10:10:10.000-07:00 for purpose of calculating expiration
        DateTimeUtils.setCurrentMillisFixed(1428340210000L);
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
    }
    
    @After
    public void aftter() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void oneWeekAfterEnrollmentAt8amExpireAfter24hours() throws Exception {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay("P1W");
        schedule.addTimes("08:00");
        schedule.setExpires("PT24H");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        schedule.setExpires("P1M");
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-30 08:00");
    }
    
    @Test
    public void onceScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 09:40");
    }
    @Test
    public void onceStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn("2015-04-10T09:00:00Z");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEndsOn("2015-03-21T10:00:00Z");
        
        // In this case the endsOn date is before the enrollment. No tasks
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        schedule.setEndsOn("2015-04-23T13:40:00Z");
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 09:40");
    }
    @Test
    public void onceStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn("2015-03-23T10:00:00Z");
        schedule.setEndsOn("2015-03-26T10:00:00Z");
        
        // Should get the second of the tasks scheduled on day of enrollment
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 13:40");
    }
    @Test
    public void onceDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:40");
    }
    @Test
    public void onceDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        schedule.setStartsOn("2015-03-27T00:00:00Z");
        
        // Again, it happens before the start date, so it doesn't happen.
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        schedule.setEndsOn(asDT("2015-04-04 10:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:40");
        
        // With a delay *after* the end date, nothing happens
        schedule.setDelay("P2M");
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2M");
        schedule.setStartsOn(asDT("2015-03-20 00:00"));
        schedule.setEndsOn(asDT("2015-06-01 10:00"));
        
        // Schedules in the window without any issue
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-05-23 09:40");
        
        schedule.setDelay("P6M");
        schedule.setStartsOn(asDT("2015-05-01 00:00"));
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(9), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceDelayExpiresScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setExpires("P1W");
        schedule.setDelay("P1M");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(asLong("2015-04-23 09:40"), tasks.get(0).getScheduledOn().getMillis());
        assertEquals(asLong("2015-04-30 09:40"), tasks.get(0).getExpiresOn().getMillis());
    }
    @Test
    public void onceExpiresScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setExpires("P1W");

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        // No tasks. Created on 3/23, it expired by 3/30, today is 4/6
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEventScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        events.put("survey:AAA:completedOn", asDT("2015-04-10 11:40"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-10 09:40");
        
        schedule.getTimes().clear();
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-10 11:40");
    }
    @Test
    public void onceEventStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-03-29 11:40"));

        // Goes to the event window day and takes the afternoon slot, after 10am
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEventEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setEndsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        // No task, the event happened after the end of the window
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEventStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-01 10:00"));
        schedule.setEndsOn(asDT("2015-05-01 10:00"));

        // No event... select the startsOn window
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        events.put("survey:AAA:completedOn", asDT("2015-04-10 00:00"));
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-10 09:40");
    }
    @Test
    public void onceEventDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-06 09:40");
        
        // This delays after the event by ~2 days, but then uses the supplied times
        schedule.setDelay("PT50H");
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-04 09:40");
        
        // If we delete the times, it delays exactly 50 hours. (2 days, 2 hours)
        schedule.getTimes().clear();
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-04 11:22");
    }
    @Test
    public void onceEventDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));

        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-06 09:40");
        
        // This should not return a task.
        schedule.setStartsOn(asDT("2015-04-15 00:00"));
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEventDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-06 09:40");
    }
    @Test
    public void onceEventDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("PT3H");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        schedule.getTimes().clear();
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(6), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-02 12:22");
    }
    @Test
    public void onceEventDelayExpiresStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("PT3H");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        schedule.setExpires("P3D");
        schedule.getTimes().clear();
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        // Given even, it would be on 4/2 at 12:22, expiring 4/5, today is 4/6
        // It's in the window but still doesn't appear
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(6), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
        
        events.put("survey:AAA:completedOn", asDT("2015-04-06 09:22"));
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(6), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(asLong("2015-04-06 12:22"), tasks.get(0).getScheduledOn().getMillis());
        assertEquals(asLong("2015-04-09 12:22"), tasks.get(0).getExpiresOn().getMillis());
    }
    @Test
    public void recurringScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        
        assertDates(tasks, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40");
    }
    @Test
    public void recurringStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setStartsOn("2015-03-20T09:00:00Z");

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(4), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);

        assertDates(tasks, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40");
    }
    @Test
    public void recurringEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEndsOn("2015-03-25T10:00:00Z"); // between the two times
        
        // In this case the endsOn date is before the enrollment. No tasks
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(11), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40");
        
        schedule.setEndsOn("2015-03-27T13:50:00Z");
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40",
                        "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setStartsOn("2015-03-23T10:00:00Z");
        schedule.setEndsOn("2015-03-27T20:00:00Z");
        
        // Should get the second of the tasks scheduled on day of enrollment
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(5), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");
        schedule.setStartsOn("2015-03-22T23:49:00Z");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(5), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");
        schedule.setEndsOn(asDT("2015-04-05 10:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40",
                        "2015-03-29 09:40", "2015-03-29 13:40");
        
        // With a delay *after* the end date, nothing happens
        schedule.setDelay("P2M");
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void recurringDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P1D");
        schedule.setStartsOn(asDT("2015-03-22 00:00"));
        schedule.setEndsOn(asDT("2015-03-30 10:00"));
        
        // Schedules in the window without any issue
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(9), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-24 09:40", "2015-03-24 13:40", "2015-03-26 09:40", "2015-03-26 13:40",
                        "2015-03-28 09:40", "2015-03-28 13:40", "2015-03-30 09:40");
        
        // Schedule before, rolls forward
        schedule.setDelay("P1M");
        schedule.setStartsOn(asDT("2015-03-30 09:00"));
        context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void recurringEventScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        events.put("survey:AAA:completedOn", asDT("2015-04-10 11:40"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-10 09:40", "2015-04-10 13:40", "2015-04-12 09:40", "2015-04-12 13:40");
    }
    @Test
    public void recurringEventStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-11 00:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-12 11:40"));

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-12 09:40", "2015-04-12 13:40");
    }
    @Test
    public void recurringEventEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setEndsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        // No task, the event happened after the end of the window
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void recurringEventStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-03-30 10:00"));
        schedule.setEndsOn(asDT("2015-04-05 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        
        assertDates(tasks, "2015-04-02 09:40", "2015-04-02 13:40", "2015-04-04 09:40", "2015-04-04 13:40");
    }
    @Test
    public void recurringEventDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(16), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-06 09:40", "2015-04-06 13:40", "2015-04-08 09:40", "2015-04-08 13:40");
    }
    @Test
    public void recurringEventDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setStartsOn(asDT("2015-04-02 00:00"));

        // The delay doesn't mean the schedule fires on this event
        events.put("survey:AAA:completedOn", asDT("2015-04-01 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);

        assertEquals(0, tasks.size());
    }
    @Test
    public void recurringEventDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setEndsOn(asDT("2015-04-08 00:00"));
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusMonths(1), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-04-06 09:40", "2015-04-06 13:40");
    }
    @Test
    public void recurringEventDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P1D");
        schedule.setStartsOn(asDT("2015-04-07 00:00"));
        schedule.setEndsOn(asDT("2015-04-10 00:00"));
        
        // This is outside the window, so when this happens, even if it recurs, it shouldn't fire
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(3), null, events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }

    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        schedule.addTimes("09:40", "13:40");
        schedule.getActivities().add(TestConstants.TEST_ACTIVITY);
        schedule.setScheduleType(type);
        if (type == RECURRING) {
            schedule.setInterval("P2D");
        }
        return schedule;
    }
    
}
