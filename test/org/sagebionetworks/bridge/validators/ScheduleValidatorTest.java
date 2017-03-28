package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.google.common.collect.Sets;

public class ScheduleValidatorTest {

    private Schedule schedule;
    ScheduleValidator validator;
    
    @Before
    public void before() {
        schedule = new Schedule();
        validator = new ScheduleValidator(Sets.newHashSet("tapTest"));
    }
    
    @Test
    public void cannotAddDuplicateTimesToSchedule() {
        Schedule schedule = new Schedule();
        schedule.addTimes(LocalTime.parse("10:00:00.000"), LocalTime.parse("10:00:00.000"));
        
        assertValidatorMessage(validator, schedule, "times", "cannot contain duplicates");
    }
    
    @Test
    public void recurringScheduleOK() {
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires(Period.parse("P1D"));
        schedule.setInterval(Period.parse("P1D"));
        schedule.addTimes(LocalTime.parse("11:30:00.000"));
        schedule.addActivity(TestUtils.getActivity3());
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void onTimeScheduleWithAdequateExpirationOK() {
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setExpires(Period.parse("PT36H"));
        schedule.addActivity(TestUtils.getActivity3());
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void persistentScheduleDoesNotHaveDelay() {
        schedule.setScheduleType(ScheduleType.PERSISTENT);
        schedule.setDelay(Period.parse("P2D"));
        schedule.addActivity(TestUtils.getActivity3());
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "should not have delay, interval, cron expression, times, or an expiration");
    }
    
    @Test
    public void persistentScheduleDoesNotHaveInterval() {
        schedule.setScheduleType(ScheduleType.PERSISTENT);
        schedule.setInterval(Period.parse("P2D"));
        schedule.addActivity(TestUtils.getActivity3());
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "should not have delay, interval, cron expression, times, or an expiration");
    }
    
    @Test
    public void persistentScheduleDoesNotHaveCronExpression() {
        schedule.setScheduleType(ScheduleType.PERSISTENT);
        schedule.setCronTrigger("asdf");
        schedule.addActivity(TestUtils.getActivity3());
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "should not have delay, interval, cron expression, times, or an expiration");
    }
    
    @Test
    public void persistentScheduleDoesNotHaveTimes() {
        schedule.setScheduleType(ScheduleType.PERSISTENT);
        schedule.addTimes(LocalTime.parse("10:00"));
        schedule.addActivity(TestUtils.getActivity3());
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "should not have delay, interval, cron expression, times, or an expiration");
    }
    
    @Test
    public void persistentScheduleDoesNotHaveExpiration() {
        schedule.setScheduleType(ScheduleType.PERSISTENT);
        schedule.setExpires(Period.parse("P1D"));
        schedule.addActivity(TestUtils.getActivity3());
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "should not have delay, interval, cron expression, times, or an expiration");
    }    
    
    @Test
    public void mustHaveAtLeastOneActivityAndAScheduleType() {
        assertValidatorMessage(validator, schedule, "activities", "are required");
        assertValidatorMessage(validator, schedule, "scheduleType", "is required");
    }
    
    @Test
    public void datesMustBeChronologicallyOrdered() {
        // make it valid except for the dates....
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.ONCE);

        DateTime startsOn = DateUtils.getCurrentDateTime();
        DateTime endsOn = startsOn.plusMinutes(10); // should be at least an hour later
        
        schedule.setStartsOn(startsOn);
        schedule.setEndsOn(endsOn);
        
        assertValidatorMessage(validator, schedule, "endsOn", "should be at least an hour after the startsOn time");
    }
    
    @Test
    public void surveyRelativePathIsTreatedAsTaskId() {
        Activity activity = TestUtils.getActivity3();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        
        DateTime now = DateUtils.getCurrentDateTime();
        schedule.setStartsOn(now);
        schedule.setEndsOn(now.plusHours(1));
        
        assertEquals(ActivityType.TASK, schedule.getActivities().get(0).getActivityType());
    }
    
    @Test
    public void rejectsScheduleWithBothCronTriggerAndInterval() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P2D");
        schedule.setCronTrigger("0 0 12 ? * TUE,THU,SAT *");
        
        assertValidatorMessage(validator, schedule, "interval",
                "and cron expression cannot both be set when a schedule repeats (results are ambiguous)");
    }
    
    @Test
    public void recurringSchedulesHasNeitherCronTriggerNorInterval() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        assertValidatorMessage(validator, schedule, "interval",
                "or cron expression must be set when a schedule repeats");
        
        schedule.setInterval("P1D");
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertNull(e.getErrors().get("Schedule"));
        }
    }
    
    @Test
    public void expiresTooShort() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setExpires("PT30M");
        
        assertValidatorMessage(validator, schedule, "expires", "must be at least one hour");
    }
    
    @Test
    public void rejectsRepeatingScheduleWithNoExpiration() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 12 1/1 * ? *");
        Activity activity = TestUtils.getActivity3();
        schedule.addActivity(activity);
        
        assertValidatorMessage(validator, schedule, "expires", "must be set if schedule repeats");
        
        schedule.setExpires("P1D");
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void rejectsInvalidCronExpression() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("2 3 a");
        
        assertValidatorMessage(validator, schedule, "cronTrigger", "is an invalid cron expression");
    }
    
    @Test
    public void rejectCronExpressionWithTimes() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 0 ? * MON *");
        schedule.addTimes("10:00");

        assertValidatorMessage(validator, schedule, "cronTrigger",
                "cannot have times (they are included in the expression)");
    }
    
    /**
     * It's ambiguous what this means when longer intervals require a time of day as well...the interval, delay, and  
     * times can all conflict with one another.
     */
    @Test
    public void rejectsInvervalOfLessThanADay() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("PT30M");
        
        assertValidatorMessage(validator, schedule, "interval", "must be at least one day");
    }
    
    @Test 
    public void rejectOneTimeActivityWithInterval() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setInterval("P2D");
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "set to once, but also has an interval and/or cron expression");
    }
    
    @Test 
    public void rejectOneTimeActivityWithCronTrigger() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setCronTrigger("0 0 8 ? * TUE,THU *");
        
        assertValidatorMessage(validator, schedule, "scheduleType",
                "set to once, but also has an interval and/or cron expression");
    }
    
    @Test
    public void schedulesWithIntervalsShouldHaveTimesOfDay() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P2D");
        
        assertValidatorMessage(validator, schedule, "times", "are required for interval-based schedules");
    }
    
    @Test
    public void intervalsShouldBeMoreThanADayLong() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("PT23H59M");
        
        assertValidatorMessage(validator, schedule, "interval", "must be at least one day");
    }
    
    @Test
    public void delaysWithTimesOfDayMustBeMoreThanOneDayLong() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addTimes("08:00");
        schedule.setDelay("PT5H");
        
        assertValidatorMessage(validator, schedule, "delay",
                "is less than one day, and times of day are also set for this schedule, which is ambiguous");
    }
    
    @Test
    public void cronScheduleValid() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        schedule.setCronTrigger("0 0 8 1/1 * ? *");
        schedule.addActivity(TestUtils.getActivity3());
        
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void intervalScheduleValid() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("08:00");
        schedule.addActivity(TestUtils.getActivity3());
        
        Validate.entityThrowingException(validator, schedule);
    }
    
}
