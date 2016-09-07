package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;

public class ScheduleContextValidatorTest {

    private ScheduleContextValidator validator = new ScheduleContextValidator();

    @Test
    public void validContext() {
        // The minimum you need to have a valid schedule context.
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier("test-id")
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withEndsOn(DateTime.now().plusDays(2))
            .withTimeZone(DateTimeZone.forOffsetHours(-3))
            .withAccountCreatedOn(DateTime.now())
            .withHealthCode("AAA")
            .withUserId("BBB")
            .build();
        
        Validate.nonEntityThrowingException(validator, context);
    }
    
    @Test
    public void requiredFields() {
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier("test").build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("offset must set a time zone offset"));
            assertTrue(e.getMessage().contains("healthCode is required"));
            assertTrue(e.getMessage().contains("userId is required"));
            assertTrue(e.getMessage().contains("endsOn is required"));
            assertTrue(e.getMessage().contains("accountCreatedOn is required"));
        }
    }

    @Test
    public void endsOnAfterNow() {
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier("study-id")
            .withTimeZone(DateTimeZone.UTC)
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
    
    @Test
    public void minimumActivitiesAreGreaterThanZero() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("study-id")
                .withMinimumPerSchedule(-1).build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("minimumPerSchedule cannot be negative"));
        }
    }
    
    @Test
    public void minimumActivitiesAreNotGreaterThanMax() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("study-id")
                .withMinimumPerSchedule(ScheduleContextValidator.MAX_MIN_ACTIVITY_COUNT + 1).build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("minimumPerSchedule cannot be greater than 5"));
        }
    }
}
