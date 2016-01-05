package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class ABTestScheduleStrategyTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ABTestScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void testScheduleCollector() {
        StudyIdentifier studyId = new StudyIdentifierImpl("test-study");
        SchedulePlan plan = TestUtils.getABTestSchedulePlan(studyId);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(3, schedules.size());
        assertEquals("Schedule 1", schedules.get(0).getLabel());
        assertEquals("Schedule 2", schedules.get(1).getLabel());
        assertEquals("Schedule 3", schedules.get(2).getLabel());
        assertTrue(schedules instanceof ImmutableList);
    }
}
