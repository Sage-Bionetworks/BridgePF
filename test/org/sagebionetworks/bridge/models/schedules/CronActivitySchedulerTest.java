package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

/**
 * Tests for the cron scheduler. Most details of the schedule object have been tested in 
 * the IntervalActivitySchedulerTask class, here we're testing some of the specifics of 
 * the cron schedules.  
 */
public class CronActivitySchedulerTest {
    
    private static final DateTime NOW = DateTime.parse("2015-03-26T14:40:00-07:00");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    private Map<String, DateTime> events;
    private List<ScheduledActivity> scheduledActivities;
    private SchedulePlan plan = new DynamoSchedulePlan();
    
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
    public void canSpecifyASequence() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.setCronTrigger("0 0 14 1/1 * ? *");
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P3D");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusWeeks(2))
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // three days of activities from enrollment
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00", "2015-03-25 14:00");
        
        // delay one day, then one day period, you get two (the first and the second which is in the day
        schedule.setSequencePeriod("P1D");
        schedule.setInterval("P1D");
        schedule.setDelay("P1D");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertDates(scheduledActivities, PST, "2015-03-24 14:00");
    }
    
    @Test
    public void sequenceCanBeOverriddenByMinCount() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P6D");
        schedule.setCronTrigger("0 0 14 1/1 * ? *");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusDays(4))
            .withMinimumPerSchedule(8)
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // Period is 6 days, you ask for 4 days ahead, but insist on 8 tasks. You should get back
        // 6 tasks... all the tasks in the period. But not 8 activities.
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00", "2015-03-25 14:00",
                "2015-03-26 14:00", "2015-03-27 14:00", "2015-03-28 14:00");
    }
     
    @Test
    public void sequenceShorterThanDaysAhead() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P2D");
        schedule.setCronTrigger("0 0 14 1/1 * ? *");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusDays(4))
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // 2 activities
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00");
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
    public void onceCronScheduleWithMultipleEventsOnlyReturnsOneActivity() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setCronTrigger("0 0 6 * * ?"); // at 6am
        schedule.setEventId("two_weeks_before_enrollment,enrollment");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan,
                getContext(ENROLLMENT.plusDays(14)));
        assertDates(scheduledActivities, "2015-03-10 06:00");
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

    @Test
    public void recurringCronScheduleWithNoEvents() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 6 * * ?"); // daily at 6am
        schedule.setEventId("non-existent-event");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(4)));
        assertTrue(scheduledActivities.isEmpty());
    }

    @Test
    public void recurringCronScheduleWithMultipleEvents() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 6 * * ?"); // daily at 6am
        schedule.setSequencePeriod("P3D");
        schedule.setEventId("two_weeks_before_enrollment,enrollment");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan,
                getContext(ENROLLMENT.plusDays(14)));
        assertDates(scheduledActivities, "2015-03-10 06:00", "2015-03-11 06:00", "2015-03-12 06:00",
                "2015-03-24 06:00", "2015-03-25 06:00", "2015-03-26 06:00");
    }

    @Test
    public void verifyTimesAreConvertedCorrectly() {
        DateTimeZone initialTimeZone = DateTimeZone.forOffsetHours(4);
        
        events.clear();
        events.put("enrollment", DateTime.parse("2016-05-12T00:04:37.000+04:00"));
        
        DateTime now = DateTime.parse("2016-05-12T17:13:13.044-07:00"); // later in a different timezone

        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(initialTimeZone)
            .withStartsOn(now)
            .withEndsOn(now.plusDays(1))
            .withEvents(events).build();
        
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 10,22 1/1 * ? *");
        schedule.setExpires("PT1H");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        List<DateTime> scheduleDates = scheduledActivities.stream()
                .map(ScheduledActivity::getScheduledOn)
                .collect(Collectors.toList());
        assertEquals("2016-05-12T22:00:00.000-07:00", scheduleDates.get(0).toString());
        assertEquals("2016-05-13T10:00:00.000-07:00", scheduleDates.get(1).toString());
        assertEquals("2016-05-13T22:00:00.000-07:00", scheduleDates.get(2).toString());
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withEvents(events).build();
    }
    
    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        // Wed. and Sat. at 9:15am
        schedule.setCronTrigger("0 15 9 ? * WED,SAT *");
        schedule.getActivities().add(TestUtils.getActivity3());
        schedule.setScheduleType(type);
        return schedule;
    }
    
}
