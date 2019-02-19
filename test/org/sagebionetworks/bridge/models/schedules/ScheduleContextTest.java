package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ClientInfo;

public class ScheduleContextTest {

    private static final ScheduleContext EMPTY_CONTEXT = new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY)
            .build();
    private static final String ENROLLMENT = "enrollment";
    private static final String HEALTH_CODE = "healthCode";

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void quietlyReturnsFalseForEvents() {
        assertNull(EMPTY_CONTEXT.getEvent(ENROLLMENT));
        assertFalse(EMPTY_CONTEXT.hasEvents());
        
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withEvents(new HashMap<String, DateTime>()).build();
        assertNull(context.getEvent(ENROLLMENT));
        assertFalse(context.hasEvents());
    }
    
    @Test(expected = NullPointerException.class)
    public void requiresStudyId() {
        new ScheduleContext.Builder().build();
    }
    
    @Test
    public void defaultsTimeZoneMinimumAndClientInfo() {
        assertEquals(ClientInfo.UNKNOWN_CLIENT, EMPTY_CONTEXT.getCriteriaContext().getClientInfo());
        assertNotNull(EMPTY_CONTEXT.getStartsOn());
        assertEquals(0, EMPTY_CONTEXT.getMinimumPerSchedule());
    }
    
    @Test
    public void verifyBuilder() {
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("app/5");
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
        DateTime startsOn = DateTime.now().minusHours(2);
        DateTime endsOn = DateTime.now();
        DateTime accountCreatedOn = DateTime.now(DateTimeZone.UTC).minusDays(2);
        
        Map<String,DateTime> events = new HashMap<>();
        events.put(ENROLLMENT, DateTime.now());
        
        // All the individual fields work
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(clientInfo)
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(PST)
                .withStartsOn(startsOn)
                .withEndsOn(endsOn)
                .withMinimumPerSchedule(3)
                .withEvents(events)
                .withHealthCode(HEALTH_CODE)
                .withAccountCreatedOn(accountCreatedOn)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withUserSubstudyIds(TestConstants.USER_SUBSTUDY_IDS).build();
        
        assertEquals(TEST_STUDY, context.getCriteriaContext().getStudyIdentifier());
        assertEquals(clientInfo, context.getCriteriaContext().getClientInfo());
        assertEquals(PST, context.getInitialTimeZone());
        assertEquals(endsOn, context.getEndsOn());
        assertEquals(events.get(ENROLLMENT), context.getEvent(ENROLLMENT));
        assertEquals(3, context.getMinimumPerSchedule());
        assertEquals(HEALTH_CODE, context.getCriteriaContext().getHealthCode());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getCriteriaContext().getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, context.getCriteriaContext().getUserSubstudyIds());
        assertEquals(startsOn, context.getStartsOn());
        assertEquals(accountCreatedOn, context.getAccountCreatedOn());

        // Test the withContext() method.
        ScheduleContext copy = new ScheduleContext.Builder().withContext(context).build();
        assertEquals(context, copy);
    }
    
    @Test
    public void verifyNulls() {
        assertNull(EMPTY_CONTEXT.getInitialTimeZone());
        assertNull(EMPTY_CONTEXT.getEndsOn());
        assertFalse(EMPTY_CONTEXT.hasEvents());
        assertNull(EMPTY_CONTEXT.getEvent(ENROLLMENT));
        assertNull(EMPTY_CONTEXT.getEndsOn());
        assertEquals(0, EMPTY_CONTEXT.getMinimumPerSchedule());
        assertNull(EMPTY_CONTEXT.getAccountCreatedOn());
        assertTrue(EMPTY_CONTEXT.getCriteriaContext().getUserDataGroups().isEmpty());
        assertTrue(EMPTY_CONTEXT.getCriteriaContext().getUserSubstudyIds().isEmpty());
        assertTrue(EMPTY_CONTEXT.getCriteriaContext().getLanguages().isEmpty());
        assertNull(EMPTY_CONTEXT.getCriteriaContext().getHealthCode());
        assertNull(EMPTY_CONTEXT.getCriteriaContext().getUserId());
        assertEquals(TEST_STUDY,EMPTY_CONTEXT.getCriteriaContext().getStudyIdentifier());
        assertEquals(ClientInfo.UNKNOWN_CLIENT, EMPTY_CONTEXT.getCriteriaContext().getClientInfo());
        // And then there's this, which is not null
        assertNotNull(EMPTY_CONTEXT.getStartsOn());
        
        // Test the withContext() method.
        ScheduleContext copy = new ScheduleContext.Builder().withContext(EMPTY_CONTEXT).build();
        assertEquals(EMPTY_CONTEXT, copy);
    }
    
    @Test
    public void verifyAccountCreatedCopy() {
        // Null is safe and works
        ScheduleContext context = new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).build();
        ScheduleContext copy = new ScheduleContext.Builder().withContext(context).build();
        assertNull(copy.getAccountCreatedOn());
        
        // Non-null is properly copied
        DateTime now = DateTime.now(DateTimeZone.UTC);
        context = new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withAccountCreatedOn(now).build();
        copy = new ScheduleContext.Builder().withContext(context).build();
        assertEquals(now, copy.getAccountCreatedOn());
    }
    
    @Test
    public void eventTimesAreForcedToUTC() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withAccountCreatedOn(DateTime.parse("2010-10-10T10:10:10.010+03:00"))
                .withStudyIdentifier("study-Id")
                .build();
        assertEquals("2010-10-10T07:10:10.010Z", context.getAccountCreatedOn().toString());
        
        ScheduleContext context2 = new ScheduleContext.Builder().withContext(context).build();
        assertEquals("2010-10-10T07:10:10.010Z", context2.getAccountCreatedOn().toString());
    }
    
}
