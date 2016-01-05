package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SimpleScheduleStrategyTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SimpleScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void testScheduleCollector() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_STUDY);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(1, schedules.size());
        assertEquals("Test label for the user", schedules.get(0).getLabel());
        assertTrue(schedules instanceof ImmutableList);
    }
}
