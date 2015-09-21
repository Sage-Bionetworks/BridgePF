package org.sagebionetworks.bridge.models.schedules;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;

public class ScheduleContextTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleContext.class).allFieldsShouldBeUsed().verify();
    }
    
}
