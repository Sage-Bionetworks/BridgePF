package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ClientInfo;

public class ScheduleContextTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void quietlyReturnsFalseForEvents() {
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).build();
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
        
        context = new ScheduleContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).withEvents(new HashMap<String, DateTime>()).build();
        assertNull(context.getEvent("enrollment"));
        assertFalse(context.hasEvents());
    }
    
    @Test(expected = NullPointerException.class)
    public void requiresStudyId() {
        new ScheduleContext.Builder().build();
    }
    
    @Test
    public void defaultsTimeZoneAndClientInfo() {
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).build();
        
        assertEquals(ClientInfo.UNKNOWN_CLIENT, context.getClientInfo());
        assertNotNull(context.getNow());
    }
    
}
