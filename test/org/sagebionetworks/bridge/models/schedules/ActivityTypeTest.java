package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ActivityTypeTest {

    @Test
    public void fromPlural() {
        assertEquals(ActivityType.TASK, ActivityType.fromPlural("tasks"));
        assertEquals(ActivityType.SURVEY, ActivityType.fromPlural("surveys"));
        assertEquals(ActivityType.COMPOUND, ActivityType.fromPlural("compoundactivities"));
        assertNull(ActivityType.fromPlural("somenonsense"));
        assertNull(ActivityType.fromPlural(""));
        assertNull(ActivityType.fromPlural(null));
    }
}
