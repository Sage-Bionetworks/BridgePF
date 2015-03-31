package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.newrelic.agent.deps.com.google.common.collect.Maps;

public class ScheduleEvaluatorTest {

    private static final DateTime ENROLLMENT_TIMESTAMP = DateTime.parse("2015-03-23T10:00:00.000-07:00");
    private static final DateTime NOW_TIMESTAMP = DateTime.parse("2015-03-26T14:40:00.000-07:00");
    private static final DateTime MEDICATION_TIMESTAMP = DateTime.parse("2015-04-02T13:42:00.000-07:00");
    
    private ScheduleEvaluator evaluator;
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
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("P7D"));
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("08:00")));
        schedule.setExpires(Period.parse("PT24H"));
        schedule.setEventId("enrollment");
        
        evaluator = new ScheduleEvaluator(schedule, events, 5);
        List<Task> tasks = evaluator.getTasks();
        
        DateTime startsOn = DateTime.parse("2015-03-30T08:00:00.000-07:00");
        DateTime endsOn = DateTime.parse("2015-03-31T08:00:00.000-07:00");
        
        assertEquals(startsOn, tasks.get(0).getStartsOn());
        assertEquals(endsOn, tasks.get(0).getEndsOn());
    }
    
    // "one month after enrollment and every month thereafter"
    @Test
    public void oneMonthAfterEnrollmentAndEveryWeekThereafter() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setDelay(Period.parse("P1M"));
        schedule.setFrequency(Period.parse("P1W"));
        schedule.setEventId("enrollment");

        evaluator = new ScheduleEvaluator(schedule, events, 1);
        List<Task> tasks = evaluator.getTasks();
        
        DateTime target = DateTime.parse("2015-04-23T10:00:00.000-07:00");
        
        assertEquals(target, tasks.get(0).getStartsOn());
        assertNull(tasks.get(0).getEndsOn());
    }
    
    // "at enrollment and every week thereafter"
    @Test
    public void atEnrollmentAndEveryWeekThereafter() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setFrequency(Period.parse("P1W"));
        schedule.setEventId("enrollment");

        evaluator = new ScheduleEvaluator(schedule, events, 1);
        List<Task> tasks = evaluator.getTasks();
        
        assertEquals(ENROLLMENT_TIMESTAMP, tasks.get(0).getStartsOn());
        assertNull(tasks.get(0).getEndsOn());
    }
    
    // "Monday and Friday at 8am, but after four hours remove the task"
    @Test
    public void monAndFri8amButAfterFourHoursRemoveTask() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * MON,FRI *");
        schedule.setExpires(Period.parse("PT4H"));
        
        evaluator = new ScheduleEvaluator(schedule, events, 6);
        List<Task> tasks = evaluator.getTasks();
        
        DateTime targetStart = DateTime.parse("2015-03-27T08:00:00.000-07:00");
        DateTime targetEnd = DateTime.parse("2015-03-27T12:00:00.000-07:00");
        assertEquals(targetStart, tasks.get(0).getStartsOn());
        assertEquals(targetEnd, tasks.get(0).getEndsOn());
    }
    
    // "an hour after taking medication, giving them an hour to complete the task"
    @Test
    public void hourAfterTakingMedicationGivingAnHourToCompleteTask() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("PT1H"));
        schedule.setExpires(Period.parse("PT1H"));
        schedule.setEventId("medication");
        
        evaluator = new ScheduleEvaluator(schedule, events, 1);
        List<Task> tasks = evaluator.getTasks();
        
        DateTime targetStart = DateTime.parse("2015-04-02T14:42:00.000-07:00");
        DateTime targetEnd = DateTime.parse("2015-04-02T15:42:00.000-07:00");
        
        assertEquals(targetStart, tasks.get(0).getStartsOn());
        assertEquals(targetEnd, tasks.get(0).getEndsOn());
    }
    
    // "wait 3 days, and then every 7 days at 2pm"
    @Test
    public void after3DaysAndThenDoSurveyEvery7DaysAt2pmTask() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("14:00")));
        schedule.setDelay(Period.parse("P3D"));
        schedule.setFrequency(Period.parse("P7D"));

        evaluator = new ScheduleEvaluator(schedule, events, 5);
        List<Task> tasks = evaluator.getTasks();
        
        // "2015-03-23T10:00:00.000-07:00"
        List<DateTime> output = Lists.newArrayList(
            DateTime.parse("2015-03-26T14:00:00.000-07:00"),
            DateTime.parse("2015-04-02T14:00:00.000-07:00"),
            DateTime.parse("2015-04-09T14:00:00.000-07:00"),
            DateTime.parse("2015-04-16T14:00:00.000-07:00"),
            DateTime.parse("2015-04-23T14:00:00.000-07:00")
        );
        verify(output, tasks);
    }
    
    // "wait 2 days, then every other day at 7am and 4pm, available for 4 hours each time"
    @Test
    public void wait2DaysThenEveryOtherDayAt7amAnd4pmTask() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setDelay(Period.parse("P2D"));
        schedule.setFrequency(Period.parse("P2D"));
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("07:00"), LocalTime.parse("16:00")));
        
        evaluator = new ScheduleEvaluator(schedule, events, 6);
        List<Task> tasks = evaluator.getTasks();
        
        assertEquals(6, tasks.size());
        
        // "2015-03-23T10:00:00.000-07:00"
        List<DateTime> output = Lists.newArrayList(
            DateTime.parse("2015-03-25T07:00:00.000-07:00"),
            DateTime.parse("2015-03-25T16:00:00.000-07:00"),
            DateTime.parse("2015-03-27T07:00:00.000-07:00"),
            DateTime.parse("2015-03-27T16:00:00.000-07:00"),
            DateTime.parse("2015-03-29T07:00:00.000-07:00"),
            DateTime.parse("2015-03-29T16:00:00.000-07:00")
        );
        verify(output, tasks);
    }
    
    // "after enrollment every day until 2015-03-26
    @Test
    public void afterEnrollmentEverydayUntil26Task() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setFrequency(Period.parse("P1D"));
        schedule.setDelay(Period.parse("P1D"));
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("08:00")));
        schedule.setEndsOn(DateTime.parse("2015-03-26T07:00:00-07:00"));
        
        evaluator = new ScheduleEvaluator(schedule, events, 20);
        List<Task> tasks = evaluator.getTasks();

        // Critically, the time is just before 8am so the 26th task should not be generated,
        // and the delay means the 23 won't be covered.
        List<DateTime> output = Lists.newArrayList(
            DateTime.parse("2015-03-24T08:00:00.000-07:00"),
            DateTime.parse("2015-03-25T08:00:00.000-07:00")
        );
        verify(output, tasks);
    }
    
    @Test
    public void scheduleOutsideWindowGeneratesNoTasks() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setFrequency(Period.parse("P1D"));
        schedule.setStartsOn(DateTime.parse("2015-03-27T07:00:00-07:00"));
        
        evaluator = new ScheduleEvaluator(schedule, events, 1);
        List<Task> tasks = evaluator.getTasks();
        
        assertEquals(0, tasks.size());
    }
    
    private void verify(List<DateTime> output, List<Task> tasks) {
        assertEquals(output.size(), tasks.size());
        for (int i=0; i < tasks.size(); i++) {
            assertEquals(output.get(i), tasks.get(i).getStartsOn());
        }
    }
    
}
