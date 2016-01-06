package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Sets;

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
    
    @Test
    public void builderWorks() {
        // User works
        User user = new User();
        user.setStudyKey("test-study");
        user.setHealthCode("AAA");
        user.setDataGroups(Sets.newHashSet("A","B"));
        
        ScheduleContext context = new ScheduleContext.Builder().withUser(user).build();
        assertEquals(user.getStudyKey(), context.getStudyIdentifier().getIdentifier());
        assertEquals(user.getHealthCode(), context.getHealthCode());
        assertEquals(user.getDataGroups(), context.getUserDataGroups());
        
        // There are defaults
        assertEquals(ClientInfo.UNKNOWN_CLIENT, context.getClientInfo());
        assertNotNull(context.getNow());
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("app/5");
        StudyIdentifier studyId = new StudyIdentifierImpl("study-key");
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
        DateTime endsOn = DateTime.now();
        Set<String> dataGroups = Sets.newHashSet("A","B");
        DateTime now = DateTime.now();
        
        Map<String,DateTime> events = new HashMap<>();
        events.put("enrollment", DateTime.now());
        
        // All the individual fields work
        context = new ScheduleContext.Builder()
                .withClientInfo(clientInfo)
                .withStudyIdentifier(studyId)
                .withTimeZone(PST)
                .withEndsOn(endsOn)
                .withEvents(events)
                .withHealthCode("healthCode")
                .withUserDataGroups(dataGroups)
                .withNow(now).build();
        assertEquals(studyId, context.getStudyIdentifier());
        assertEquals(clientInfo, context.getClientInfo());
        assertEquals(PST, context.getZone());
        assertEquals(endsOn, context.getEndsOn());
        assertEquals(events.get("enrollment"), context.getEvent("enrollment"));
        assertEquals("healthCode", context.getHealthCode());
        assertEquals(dataGroups, context.getUserDataGroups());
        assertEquals(now, context.getNow());

        // and the other studyId setter
        context = new ScheduleContext.Builder().withStudyIdentifier("study-key").build();
        assertEquals(studyId, context.getStudyIdentifier());
    }
    
}
