package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.sagebionetworks.bridge.TestConstants.TEST_3_ACTIVITY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

/**
 * Tests for the cron scheduler. Most details of the schedule object have been tested in 
 * the IntervalActivitySchedulerTask class, here we're testing some of the specifics of 
 * the cron schedules.  
 */
public class CronActivitySchedulerTest {
    
    private Map<String, DateTime> events;
    private List<ScheduledActivity> scheduledActivities;
    private SchedulePlan plan = new DynamoSchedulePlan();
    
    private DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    @Before
    public void before() {
        plan.setGuid("BBB");
        
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
        events.put("two_weeks_before_enrollment", ENROLLMENT.minusWeeks(2));
        events.put("two_months_before_enrollment", ENROLLMENT.minusMonths(2));
    }
    
    @Test
    public void onceCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertDates(scheduledActivities, "2015-03-25 09:15");
    }
    @Test
    public void onceStartsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        // This is the day after the event will be scheduled, based on enrollment date
        schedule.setStartsOn(asDT("2015-03-31 00:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertEquals(0, scheduledActivities.size());
    }
    @Test
    public void onceEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(scheduledActivities, "2015-03-25 09:15");
    }
    @Test
    public void onceStartEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn(asDT("2015-03-23 00:00"));
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(scheduledActivities, "2015-03-25 09:15");
    }
    @Test
    public void onceDelayCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(scheduledActivities, "2015-03-28 09:15");
    }
    @Test
    public void recurringCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        assertDates(scheduledActivities, "2015-03-25 09:15", "2015-03-28 09:15", 
            "2015-04-01 09:15", "2015-04-04 09:15", "2015-04-08 09:15", "2015-04-11 09:15");
    }
    @Test
    public void recurringEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(scheduledActivities, "2015-03-25 09:15", "2015-03-28 9:15");
    }
    @Test
    public void onceCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertDates(scheduledActivities, "2015-03-23 13:00");
        
    }
    @Test
    public void rercurringCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(2)));
        assertDates(scheduledActivities, "2015-03-23 13:00", "2015-03-23 20:00", "2015-03-24 10:00", "2015-03-24 13:00", "2015-03-24 20:00");
    }
    @Test
    public void recurringCronScheduleAgainstCalculatedEventWorks() {
        DateTimeUtils.setCurrentMillisFixed(ENROLLMENT.getMillis());
        
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 6 ? * SAT *"); // Saturdays at 6am
        schedule.setExpires(Period.parse("P7D"));
        schedule.setEventId("two_weeks_before_enrollment");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(4)));
        // Ultimate result: It's the 23rd, and right at enrollment, you see this task from the 21st.
        assertDates(scheduledActivities, "2015-03-21 06:00");
        assertEquals(ScheduledActivityStatus.AVAILABLE, scheduledActivities.get(0).getStatus());
        DateTimeUtils.setCurrentMillisSystem();
    }
    @Test
    // All the dates in this test are derived from BRIDGE-1211.
    public void recurringCronScheduleWithStartEndTimeWindowWorks() {
        DateTime now = DateTime.parse("2016-03-15T17:13:13.044Z");
        DateTimeUtils.setCurrentMillisFixed(now.getMillis());
        
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 0 ? * MON *");
        schedule.setEventId("two_weeks_before_enrollment");
        schedule.setStartsOn(DateTime.parse("2016-03-14T00:00:00.000Z"));
        schedule.setEndsOn(DateTime.parse("2016-03-20T23:59:00.000Z"));
        schedule.setExpires("P7D");
        
        events.clear();
        events.put("enrollment", DateTime.parse("2016-03-02T00:04:37.000Z"));
        events.put("two_weeks_before_enrollment", events.get("enrollment").minusWeeks(2));
        
        // We'd expect, based on that schedule, to have a task:
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(now.plusDays(4)));
        assertDates(scheduledActivities, "2016-03-14 00:00");
        
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    // All the dates in this test are derived from BRIDGE-1211.
    public void recurringCronScheduleWithEndsOnTimeWindowWorks() {
        // This query will be a couple of months in the future, against a future absolute time window.
        // We should get the task for that specific week, and nothing else. Query is on 5/12.
        // We'll extend the context well into the future in order to get back that task.
        DateTime now = DateTime.parse("2016-05-12T17:13:13.044Z");
        DateTimeUtils.setCurrentMillisFixed(now.getMillis());
        
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 0 ? * MON *");
        schedule.setEventId("two_weeks_before_enrollment");
        schedule.setEndsOn(DateTime.parse("2016-05-09T00:00:00.000Z"));
        schedule.setExpires("P7D");
        
        events.clear();
        events.put("enrollment", DateTime.parse("2016-03-15T00:04:37.000Z"));
        events.put("two_weeks_before_enrollment", events.get("enrollment").minusWeeks(2));
        
        // If we search far enough ahead, we'll get back the one task in the schedule window
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(now.plusDays(180)));
        assertDates(scheduledActivities, "2016-05-09 00:00");
        
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withEvents(events).build();
    }
    
    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        // Wed. and Sat. at 9:15am
        schedule.setCronTrigger("0 15 9 ? * WED,SAT *");
        schedule.getActivities().add(TEST_3_ACTIVITY);
        schedule.setScheduleType(type);
        return schedule;
    }
    
}
