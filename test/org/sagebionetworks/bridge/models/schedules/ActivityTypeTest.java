package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ActivityTypeTest {

    @Test
    public void pluralToType() {
        assertEquals(ActivityType.TASK, ActivityType.PLURALS.get("tasks"));
        assertEquals(ActivityType.SURVEY, ActivityType.PLURALS.get("surveys"));
        assertEquals(ActivityType.COMPOUND, ActivityType.PLURALS.get("compoundactivities"));
        assertNull(ActivityType.PLURALS.get("boguses"));
        assertNull(ActivityType.PLURALS.get(null));
    }
    
}
