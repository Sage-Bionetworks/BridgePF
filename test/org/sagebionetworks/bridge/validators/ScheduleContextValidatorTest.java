package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class ScheduleContextValidatorTest {

    private ScheduleContextValidator validator = new ScheduleContextValidator();
    
    @Test
    public void studyIdentifierTimeZoneHealthCodeAndEndsOnAlwaysRequired() {
        ScheduleContext context = new ScheduleContext(null, null, null, null, null, null);
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("studyId is required"));
            assertTrue(e.getMessage().contains("offset must set a time zone offset"));
            assertTrue(e.getMessage().contains("healthCode is required"));
            assertTrue(e.getMessage().contains("endsOn is required"));
        }
    }

    @Test
    public void endsOnAfterNow() {
        ScheduleContext context = new ScheduleContext(new StudyIdentifierImpl("study-id"), DateTimeZone.UTC, DateTime.now().minusHours(1), "healthCode", null, null);
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("endsOn must be after the time of the request"));
        }
    }
    
    @Test
    public void endsOnBeforeMaxNumDays() {
        // We document 4 days because we're going to move the time to the end of the day and that 
        // effectively means you can only ask for one day less than the MAX_EXPIRES_ON_DAYS.
        DateTime endsOn = DateTime.now().plusDays(ScheduleContextValidator.MAX_EXPIRES_ON_DAYS).withHourOfDay(23)
                        .withMinuteOfHour(59).withSecondOfMinute(59);
        
        ScheduleContext context = new ScheduleContext(new StudyIdentifierImpl("study-id"), DateTimeZone.UTC, endsOn, "healthCode", null, null);
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().contains("endsOn must be 5 days or less"));
        }
    }
    
    
    
}
