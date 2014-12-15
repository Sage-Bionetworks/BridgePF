package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

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
    public void cannotSubmitScheduleWithDifferentRefAndSurveyKeys() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("asdf", DateUtils.getCurrentMillisFromEpoch());
        
        Activity activity = new Activity("Label", ActivityType.SURVEY, "https://parkinson-staging.sagebridge.org/api/v1/surveys/d28969cc-70d7-40cc-9535-fe3f3120a85f/2014-11-26T21:41:03.819Z", keys);
        
        schedule.addActivity(activity);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            List<String> errors = e.getErrors().get("activities[0].survey.guid");
            assertEquals(1, errors.size());
            assertEquals("activities[0].survey.guid does not match the URL for this activity", errors.get(0));
        }
    }
    
    @Test
    public void activityMustBeFullyInitialized() {
        Activity activity = new Activity(null, null, null, null);
        
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
        schedule.addActivity(new Activity("Label", ActivityType.TASK, "task:AAA"));
        schedule.setScheduleType(ScheduleType.ONCE);
        
        long startsOn = DateUtils.getCurrentMillisFromEpoch();
        long endsOn = startsOn + 1; // should be at least an hour later
        
        schedule.setStartsOn(startsOn);
        schedule.setEndsOn(endsOn);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("endsOn should be at least an hour after the startsOn time", e.getErrors().get("endsOn").get(0));
        }
        
        endsOn = startsOn + (60 * 60 * 1000);
        schedule.setEndsOn(endsOn);
        Validate.entityThrowingException(validator, schedule);
    }
    
}
