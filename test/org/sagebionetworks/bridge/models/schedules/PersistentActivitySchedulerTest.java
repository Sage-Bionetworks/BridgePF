package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
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
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

public class PersistentActivitySchedulerTest {
    
    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    private Map<String, DateTime> events;
    private List<ScheduledActivity> scheduledActivities;
    private SchedulePlan plan;
    private Schedule schedule;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(1428340210000L);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");

        events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        schedule = new Schedule();
        schedule.setEventId("enrollment");
        schedule.getActivities().add(TestConstants.TEST_3_ACTIVITY);
        schedule.setScheduleType(ScheduleType.PERSISTENT);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void scheduleWorks() {
        // enrollment "2015-03-23T10:00:00Z"
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertDates(scheduledActivities, "2015-03-23 00:00");
    }
    @Test
    public void startsOnScheduleWorks() {
        schedule.setStartsOn("2015-04-10T09:00:00Z");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertEquals(0, scheduledActivities.size());
    }
    @Test
    public void endsOnScheduleWorks() {
        // enrollment "2015-03-23T10:00:00Z"
        schedule.setEndsOn("2015-03-21T10:00:00Z");
        
        // In this case the endsOn date is before the enrollment. No activities.
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(0, scheduledActivities.size());
        
        schedule.setEndsOn("2015-04-23T13:40:00Z");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-23 00:00");
    }
    @Test
    public void startEndsOnScheduleWorks() {
        // Because we're shifting time to midnight, we need to change this time to midnight
        // or this test will not pass, and that's expected.
        schedule.setStartsOn("2015-03-23T00:00:00Z");
        schedule.setEndsOn("2015-03-26T10:00:00Z");
        
        // Should get one activity
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-23 00:00");
    }
    @Test
    public void sequenceOfEventsWorks() {
        // starts when a different ask is completed
        schedule.setEventId("survey:AAA:finished");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(0, scheduledActivities.size());
        
        // Once that occurs, a task is issued for "right now"
        events.put("survey:AAA:finished", asDT("2015-04-10 11:40"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-10 00:00");
        
        // and it's reissued any time that task itself is completed.
        events.put("activity:"+schedule.getActivities().get(0).getGuid()+":finished", asDT("2015-04-12 09:40"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(2)));
        assertDates(scheduledActivities, "2015-04-12 00:00");
    }
    @Test
    public void originalPersistentScheduleStructureStillWorks() {
        schedule.setEventId("activity:AAA:finished,enrollment");
        schedule.setScheduleType(ScheduleType.ONCE);
        
        assertTrue(schedule.getPersistent());
        assertTrue(TestConstants.TEST_3_ACTIVITY.isPersistentlyRescheduledBy(schedule));
        assertTrue(schedule.schedulesImmediatelyAfterEvent());
        
        schedule.setEventId("activity:BBB:finished,enrollment");
        schedule.setDelay("P1D");
        assertFalse(schedule.getPersistent());
        assertFalse(TestConstants.TEST_3_ACTIVITY.isPersistentlyRescheduledBy(schedule));
        assertFalse(schedule.schedulesImmediatelyAfterEvent());
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withHealthCode("AAA")
            .withEvents(events).build();
    }
}
