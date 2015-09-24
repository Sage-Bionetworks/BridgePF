package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.junit.Test;

public class ScheduleContextTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void quietlyReturnsFalseForEvents() {
        ScheduleContext context = new ScheduleContext.Builder().build();
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
        
        context = new ScheduleContext.Builder().withEvents(new HashMap<String, DateTime>()).build();
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
    }
    
}
