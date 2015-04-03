package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.newrelic.agent.deps.com.google.common.collect.Maps;

public class TaskSchedulerTest {

    private static final DateTime ENROLLMENT_TIMESTAMP = DateTime.parse("2015-03-23T10:00:00.000-07:00");
    private static final DateTime NOW_TIMESTAMP = DateTime.parse("2015-03-26T14:40:00.000-07:00");
    private static final DateTime MEDICATION_TIMESTAMP = DateTime.parse("2015-04-02T13:42:00.000-07:00");
    
    private Map<String,DateTime> events;
    
    @Before
    public void before() {
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am PTD
        events.put("enrollment", ENROLLMENT_TIMESTAMP);
        // Now is April 2nd, 2015 @ 2:40pm, PDT
        events.put("now", NOW_TIMESTAMP);
        // Last took medication on April 2nd, 2015 @ 1:42pm PDT
        events.put("medication", MEDICATION_TIMESTAMP);
    }
    
    // "one week after enrollment at 8am, expire after 24 hours"
    @Test
    public void oneWeekAfterEnrollmentAt8AM() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P7D");
        schedule.setTimes("08:00");
        schedule.setExpires("PT24H");
        schedule.setEventId("enrollment");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(5);
        
        assertDate("2015-03-30T08:00:00.000-07:00", tasks.get(0).getStartsOn());
        assertDate("2015-03-31T08:00:00.000-07:00", tasks.get(0).getEndsOn());
    }
    
    // "one month after enrollment and every month thereafter"
    @Test
    public void oneMonthAfterEnrollmentAndEveryWeekThereafter() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setDelay("P1M");
        schedule.setInterval("P1W");
        schedule.setEventId("enrollment");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        
        assertDate("2015-04-23T10:00:00.000-07:00", tasks.get(0).getStartsOn());
        assertNull(tasks.get(0).getEndsOn());
    }
    
    // "at enrollment and every week thereafter"
    @Test
    public void atEnrollmentAndEveryWeekThereafter() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setInterval("P1W");
        schedule.setEventId("enrollment");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        
        assertEquals(ENROLLMENT_TIMESTAMP, tasks.get(0).getStartsOn());
        assertNull(tasks.get(0).getEndsOn());
    }
    
    // "Monday and Friday at 8am, but after four hours remove the task"
    @Test
    public void monAndFri8amButAfterFourHoursRemoveTask() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setCronTrigger("0 0 8 ? * MON,FRI *");
        schedule.setExpires("PT4H");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(6);
        
        // This is the first friday after 3/23 enrollment. But this is wrong, because
        // it's not supposed to create tasks that end before "now". We don't want 
        // to iterate through the whole list from the beginning every time.
        assertDate("2015-03-27T08:00:00.000-07:00", tasks.get(0).getStartsOn());
        assertDate("2015-03-27T12:00:00.000-07:00", tasks.get(0).getEndsOn());
    }
    
    // "an hour after taking medication, giving them an hour to complete the task"
    @Test
    public void hourAfterTakingMedicationGivingAnHourToCompleteTask() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(ONCE);
        schedule.setDelay("PT1H");
        schedule.setExpires("PT1H");
        schedule.setEventId("medication");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        
        assertDate("2015-04-02T14:42:00.000-07:00", tasks.get(0).getStartsOn());
        assertDate("2015-04-02T15:42:00.000-07:00", tasks.get(0).getEndsOn());
    }
    
    // "wait 3 days, and then every 7 days at 2pm"
    @Test
    public void after3DaysAndThenDoSurveyEvery7DaysAt2pmTask() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setTimes("14:00");
        schedule.setDelay("P3D");
        schedule.setInterval("P7D");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(5);
        
        // "2015-03-23T10:00:00.000-07:00"
        List<String> output = Lists.newArrayList(
            "2015-03-26T14:00:00.000-07:00",
            "2015-04-02T14:00:00.000-07:00",
            "2015-04-09T14:00:00.000-07:00",
            "2015-04-16T14:00:00.000-07:00",
            "2015-04-23T14:00:00.000-07:00"
        );
        assertDates(output, tasks);
    }
    
    // "wait 2 days, then every other day at 7am and 4pm, available for 4 hours each time"
    @Test
    public void wait2DaysThenEveryOtherDayAt7amAnd4pmTask() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setDelay("P2D");
        schedule.setInterval("P2D");
        schedule.setTimes("07:00", "16:00");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(6);
        
        // "2015-03-23T10:00:00.000-07:00"
        List<String> output = Lists.newArrayList(
            "2015-03-25T07:00:00.000-07:00",
            "2015-03-25T16:00:00.000-07:00",
            "2015-03-27T07:00:00.000-07:00",
            "2015-03-27T16:00:00.000-07:00",
            "2015-03-29T07:00:00.000-07:00",
            "2015-03-29T16:00:00.000-07:00"
        );
        
        assertEquals(6, tasks.size());
        assertDates(output, tasks);
    }
    
    // "after enrollment every day until 2015-03-26
    @Test
    public void afterEnrollmentEverydayUntilMarch26Task() throws Exception {
        // Now: 2015-03-26 14:40:00
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setInterval("P1D");
        schedule.setDelay("P1D");
        schedule.setTimes("08:00");
        schedule.setEndsOn("2015-03-26T07:00:00-07:00");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(20);

        List<String> output = Lists.newArrayList(
            "2015-03-24T08:00:00.000-07:00", 
            "2015-03-25T08:00:00.000-07:00"
        );
        assertDates(output, tasks);
    }
    
    @Test
    public void afterEnrollmentEverydayUntilMarch26TaskEntirelyInPastReturnsNoTasks() {
        events.put("now", DateTime.parse("2015-04-10T07:00:00-07:00"));
        
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setInterval("P1D");
        schedule.setDelay("P1D");
        schedule.setTimes("08:00");
        schedule.setEndsOn("2015-03-26T07:00:00-07:00");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(20);

        List<String> output = Lists.newArrayList(
            "2015-03-24T08:00:00.000-07:00", 
            "2015-03-25T08:00:00.000-07:00"
        );
        assertDates(output, tasks);
        
    }
    
    @Test
    public void scheduleOutsideWindowGeneratesNoTasks() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setInterval("P1D");
        schedule.setStartsOn("2014-05-15T07:00:00-07:00");// before "now"
        schedule.setEndsOn("2015-02-27T07:00:00-07:00");// before "now"
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        assertEquals(0, tasks.size());
        
        // change now to be before startsOn
        events.put("now", DateTime.parse("2014-03-15T07:00:00-07:00"));
        
        tasks = new TaskScheduler(schedule, events).getTasks(1);
        assertEquals(0, tasks.size());
    }
    
    /**
     * Does it matter if the dependent tasks are ONCE or RECURRING? And how do chains work if the scheduler is asked for
     * a smaller number of tasks?
     * 
     * @throws Exception
     */
   
    @Test
    public void tasksCanBeChainedTogether() throws Exception {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity("Task #1", "task1"));
        schedule.setScheduleType(RECURRING);
        schedule.setDelay("P1M");
        
        Schedule schedule2 = new Schedule();
        schedule2.getActivities().add(new Activity("Task #2", "task2"));
        schedule.setScheduleType(ONCE);
        schedule2.setEventId("task:task1");

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        assertDates(Lists.newArrayList("2015-04-23T10:00:00.000-07:00"), tasks);

        tasks = new TaskScheduler(schedule2, events).getTasks(1);
        assertEquals(0, tasks.size()); // nothing has happened yet
        
        DateTime TASK1_EVENT = DateTime.parse("2015-04-25T15:32:14.023-07:00");
        
        // Now say that task was finished a couple of days after that:
        events.put("task:task1", TASK1_EVENT);
        
        tasks = new TaskScheduler(schedule2, events).getTasks(1);
        assertEquals(TASK1_EVENT, tasks.get(0).getStartsOn());
    }
    
    /**
     * Task #2 is created immediately after Task #1. No matter when the scheduler runs (no matter when "now" is), Task
     * #2 always needs to have the same timestamp, as long as Task #1 has the same timestamp. Otherwise we can not
     * detect that the task already exists.
     * 
     * @throws Exception
     */
    @Test
    public void taskSequencesGeneratedAtDifferentTimesAreTheSame() throws Exception {
        events.put("anEvent", DateTime.parse("2015-04-12T08:31:26.345-07:00"));
        
        Schedule schedule = createSchedule();
        schedule.setScheduleType(RECURRING);
        schedule.setEventId("anEvent");
        schedule.setInterval("PT3H");
        schedule.setDelay("PT1H");
        
        List<String> output = Lists.newArrayList("2015-04-12T09:31:26.345-07:00", "2015-04-12T12:31:26.345-07:00");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(2);
        assertDates(output, tasks);
        
        events.put("now", DateTime.parse("2015-04-13T08:00:00.000-07:00"));
        tasks = new TaskScheduler(schedule, events).getTasks(2);
        assertDates(output, tasks);
    }
    
    /**
     * Since we test the uniqueness of a task based on timestamp, we need to account for situations where two tasks have
     * the same timestamp (e.g. two schedules that create tasks after enrollment, assuming no delay, will have the same
     * timestamp... there must be something about them that allows you to tell them apart).
     * 
     * @throws Exception
     */
    @Test
    public void twoTasksWithSameTimestampCanBeDifferentiated() throws Exception {
        // Add second activity to same schedule
        Schedule schedule = createSchedule();
        schedule.getActivities().add(new Activity("Label2", "gaitTest"));
        schedule.setScheduleType(ONCE);

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(10);
        
        assertEquals(2, tasks.size());
        assertNotEquals(tasks.get(0), tasks.get(1));
    }
    
    /**
     * Although scheduler works forward from events, including the initial enrollment event, it should
     * not return any task that's entirely in the past. The catch is any schedule that has no expires 
     * or endsOn value. These will accumulate each time.
     */
    @Test
    public void schedulerDoesNotReturnTasksInPast() throws Exception {
        events.put("now", DateTime.parse("2015-04-20T14:40:00.000-07:00"));
        
        Schedule schedule = createSchedule();
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1D");
        schedule.setExpires("P1D");
        
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        
        assertEquals(0, tasks.size());
    }
    
    @Test
    public void noTaskCreatedIfNoEventIdExists() throws Exception {
        Schedule schedule = createSchedule();
        schedule.setEventId("anEvent"); // doesn't exist
        schedule.setScheduleType(ONCE);

        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        
        assertEquals(0, tasks.size()); // so no task created
    }
    
    @Test
    public void taskThatIsAlwaysAvailable() throws Exception {
        // Just fire this each time it is itself completed, and it never expires.
        Schedule schedule = createSchedule();
        schedule.setEventId("anEvent");
        schedule.setScheduleType(ONCE);
        
        events.put("anEvent", events.get("now").minusHours(3));
        List<Task> tasks = new TaskScheduler(schedule, events).getTasks(1);
        assertEquals(events.get("now").minusHours(3), tasks.get(0).getStartsOn());
        
        events.put("anEvent", events.get("now").plusHours(8));
        new TaskScheduler(schedule, events).getTasks(1);
        assertEquals(events.get("now").plusHours(8), tasks.get(0).getStartsOn());
    }
    
    private Schedule createSchedule() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity("Label", "tapTest"));
        return schedule;
    }
    
    private void assertDate(String date, DateTime dateTime) {
        assertEquals(DateTime.parse(date), dateTime);
    }
    
    private void assertDates(List<String> output, List<Task> tasks) {
        assertEquals(output.size(), tasks.size());
        for (int i=0; i < tasks.size(); i++) {
            assertEquals(DateTime.parse(output.get(i)), tasks.get(i).getStartsOn());
        }
    }
}
