package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.sagebionetworks.bridge.TestConstants.TEST_ACTIVITY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;

/**
 * Tests for the cron scheduler. Most details of the schedule object have been tested in 
 * the IntervalTaskSchedulerTask class, here we're testing some of the specifics of 
 * the cron schedules.  
 */
public class CronTaskSchedulerTest {
    
    private Map<String, DateTime> events;
    private List<Task> tasks;
    
    private DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    @Before
    public void before() {
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
    }
    
    @Test
    public void onceCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);

        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceStartsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn(asDT("2015-03-31 00:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceStartEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn(asDT("2015-03-23 00:00"));
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceDelayCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-28 09:15");
    }
    @Test
    public void recurringCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(3), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:15", "2015-03-28 09:15", 
            "2015-04-01 09:15", "2015-04-04 09:15", "2015-04-08 09:15", "2015-04-11 09:15");
    }
    @Test
    public void recurringEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-25 09:15", "2015-03-28 9:15");
    }
    @Test
    public void onceCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusWeeks(1), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 13:00");
        
    }
    @Test
    public void rercurringCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        ScheduleContext context = new ScheduleContext(DateTimeZone.UTC, ENROLLMENT.plusDays(2), events, null);
        tasks = schedule.getScheduler().getTasks(context);
        assertDates(tasks, "2015-03-23 13:00", "2015-03-23 20:00", "2015-03-24 10:00", "2015-03-24 13:00", "2015-03-24 20:00");
    }
    
    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        // Wed. and Sat. at 9:15am
        schedule.setCronTrigger("0 15 9 ? * WED,SAT *");
        schedule.getActivities().add(TEST_ACTIVITY);
        schedule.setScheduleType(type);
        return schedule;
    }
    
}
