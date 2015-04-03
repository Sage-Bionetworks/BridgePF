package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

public class ScheduleValidatorTest {

    private Schedule schedule;
    ScheduleValidator validator;
    
    @Before
    public void before() {
        schedule = new Schedule();
        validator = new ScheduleValidator();
    }
    
    @Test
    public void mustHaveAtLeastOneActivity() {
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals(1, e.getErrors().get("activities").size());
        }
    }
    
    @Test
    public void activityMustBeFullyInitialized() {
        Activity activity = new Activity(null, null);
        
        schedule.addActivity(activity);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("scheduleType cannot be null", e.getErrors().get("scheduleType").get(0));
            assertEquals("activities[0].activityType cannot be null", e.getErrors().get("activities[0].activityType").get(0));
            assertEquals("activities[0].ref cannot be missing, null, or blank", e.getErrors().get("activities[0].ref").get(0));
        }
    }
    
    @Test
    public void datesMustBeChronologicallyOrdered() {
        // make it valid except for the dates....
        schedule.addActivity(new Activity("Label", "task:AAA"));
        schedule.setScheduleType(ScheduleType.ONCE);

        DateTime startsOn = DateUtils.getCurrentDateTime();
        DateTime endsOn = startsOn.plusMinutes(10); // should be at least an hour later
        
        schedule.setStartsOn(startsOn);
        schedule.setEndsOn(endsOn);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("endsOn should be at least an hour after the startsOn time", e.getErrors().get("endsOn").get(0));
        }
    }
    
    @Test
    public void surveyRelativePathIsInvalid() {
        schedule.addActivity(new Activity("Label", "/api/v1/surveys/AAA/published"));
        schedule.setScheduleType(ScheduleType.ONCE);
        
        DateTime now = DateUtils.getCurrentDateTime();
        schedule.setStartsOn(now);
        schedule.setEndsOn(now.plusHours(1));

        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("must be an absolute URL to a survey resource API"));
        }
    }
    
    @Test
    public void activityCorrectlyParsesPublishedSurveyPath() {
        Activity activity = new Activity("Label", "https://server/api/v1/surveys/AAA/published");
        
        SurveyReference ref = activity.getSurvey();
        assertEquals("AAA", ref.getGuid());
        assertNull(ref.getCreatedOn());
        
        activity = new Activity("Label", "task:AAA");
        assertNull(activity.getSurvey());
        
        activity = new Activity("Label", "https://server/api/v1/surveys/AAA/2015-01-27T17:46:31.237Z");
        ref = activity.getSurvey();
        assertEquals("AAA", ref.getGuid());
        assertEquals("2015-01-27T17:46:31.237Z", ref.getCreatedOn());
    }
    
    @Test
    public void mustHaveCronTriggerOrInterval() {
        Schedule schedule = new Schedule();
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("Schedule should have either a cron expression, or an interval", e.getErrors().get("Schedule").get(0));
        }
        
        schedule.setInterval("P1D");
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertNull(e.getErrors().get("Schedule"));
        }
    }
    
    @Test
    public void rejectsInvalidCronExpression() {
        Schedule schedule = new Schedule();
        schedule.setCronTrigger("2 3 a");

        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("cronTrigger is an invalid cron expression", e.getErrors().get("cronTrigger").get(0));
        }
    }
}
