package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;

public class ScheduleContextValidatorTest {

    private ScheduleContextValidator validator = new ScheduleContextValidator();
    
    @Test
    public void studyIdentifierTimeZoneHealthCodeAndEndsOnAlwaysRequired() {
        ScheduleContext context = new ScheduleContext.Builder().build();
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
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier("study-id").withTimeZone(DateTimeZone.UTC)
            .withEndsOn(DateTime.now().minusHours(1)).withHealthCode("healthCode").build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("endsOn must be after the time of the request"));
        }
    }
    
    @Test
    public void endsOnBeforeMaxNumDays() {
        // Setting this two days past the maximum. Will always fail.
        DateTime endsOn = DateTime.now().plusDays(ScheduleContextValidator.MAX_EXPIRES_ON_DAYS+2);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier("study-id").withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn).withHealthCode("healthCode").build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("endsOn must be 5 days or less"));
        }
    }
    
    
    
}
