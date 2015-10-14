package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.ImmutableList;

public class SimpleScheduleStrategyTest {

    @Test
    public void testScheduleCollector() {
        StudyIdentifier studyId = new StudyIdentifierImpl("test-study");
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(studyId);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(1, schedules.size());
        assertEquals("Test label for the user", schedules.get(0).getLabel());
        assertTrue(schedules instanceof ImmutableList);
    }
}
