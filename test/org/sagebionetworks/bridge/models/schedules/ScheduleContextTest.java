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
        ScheduleContext context = new ScheduleContext(null, null, null, null, null, null);
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
        
        context = new ScheduleContext(null, null, null, null, new HashMap<String,DateTime>(), null);
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
    }
    
}
