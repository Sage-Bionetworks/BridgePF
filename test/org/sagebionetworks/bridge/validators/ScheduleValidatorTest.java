package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
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
    public void mustHaveAtLeastOneActivityAndAScheduleType() {
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("activities are required", e.getErrors().get("activities").get(0));
            assertEquals("scheduleType is required", e.getErrors().get("scheduleType").get(0));
        }
    }
    
    @Test
    public void datesMustBeChronologicallyOrdered() {
        // make it valid except for the dates....
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        schedule.setScheduleType(ScheduleType.ONCE);

        DateTime startsOn = DateUtils.getCurrentDateTime();
        DateTime endsOn = startsOn.plusMinutes(10); // should be at least an hour later
        
        schedule.setStartsOn(startsOn);
        schedule.setEndsOn(endsOn);
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("endsOn should be at least an hour after the startsOn time", e.getErrors().get("endsOn").get(0));
        }
    }
    
    @Test
    public void surveyRelativePathIsTreatedAsTaskId() {
        Activity activity = TestConstants.TEST_3_ACTIVITY;
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
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            System.out.println(e.getErrors());
            assertEquals("interval and cron expression cannot both be set when a schedule repeats (results are ambiguous)", e.getErrors().get("interval").get(0));
        }
    }
    
    @Test
    public void recurringSchedulesHasNeitherCronTriggerNorInterval() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("interval or cron expression must be set when a schedule repeats", e.getErrors().get("interval").get(0));
        }
        
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
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("expires must be at least one hour", e.getErrors().get("expires").get(0));
        }
    }
    
    @Test
    public void rejectsRepeatingScheduleWithNoExpiration() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 12 1/1 * ? *");
        Activity activity = TestConstants.TEST_3_ACTIVITY;
        schedule.addActivity(activity);
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("expires must be set if schedule repeats", e.getErrors().get("expires").get(0));
        }
        
        schedule.setExpires("P1D");
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void rejectsInvalidCronExpression() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("2 3 a");

        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("cronTrigger is an invalid cron expression", e.getErrors().get("cronTrigger").get(0));
        }
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
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("interval must be at least one day", e.getErrors().get("interval").get(0));
        }
    }
    
    @Test 
    public void rejectOneTimeActivityWithInterval() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setInterval("P2D");
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("scheduleType set to once, but also has an interval and/or cron expression", e.getErrors().get("scheduleType").get(0));
        }
    }
    
    @Test 
    public void rejectOneTimeActivityWithCronTrigger() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setCronTrigger("0 0 8 ? * TUE,THU *");
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("scheduleType set to once, but also has an interval and/or cron expression", e.getErrors().get("scheduleType").get(0));
        }
    }
    
    @Test
    public void schedulesWithIntervalsShouldHaveTimesOfDay() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P2D");
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("times are required for interval-based schedules", e.getErrors().get("times").get(0));
        }
    }
    
    @Test
    public void intervalsShouldBeMoreThanADayLong() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("PT23H59M");
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("interval must be at least one day", e.getErrors().get("interval").get(0));
        }
    }
    
    @Test
    public void delaysWithTimesOfDayMustBeMoreThanOneDayLong() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addTimes("08:00");
        schedule.setDelay("PT5H");
        
        try {
            Validate.entityThrowingException(validator, schedule);
            fail("Should have thrown InvalidEntityException");
        } catch(InvalidEntityException e) {
            assertEquals("delay is less than one day, and times of day are also set for this schedule, which is ambiguous", e.getErrors().get("delay").get(0));
        }
    }
    
    @Test
    public void cronScheduleValid() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        schedule.setCronTrigger("0 0 8 1/1 * ? *");
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        
        Validate.entityThrowingException(validator, schedule);
    }
    
    @Test
    public void intervalScheduleValid() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("08:00");
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        
        Validate.entityThrowingException(validator, schedule);
        
    }
    
}
