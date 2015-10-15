package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoScheduledActivityTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoScheduledActivity.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canRoundtripSerialize() throws Exception {
        LocalDateTime scheduledOn = LocalDateTime.now().plusWeeks(1);
        LocalDateTime expiresOn = LocalDateTime.now().plusWeeks(1);
        
        String scheduledOnString = scheduledOn.toDateTime(DateTimeZone.UTC).toString();
        String expiresOnString = expiresOn.toDateTime(DateTimeZone.UTC).toString();
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setActivity(TestConstants.TEST_3_ACTIVITY);
        schActivity.setLocalScheduledOn(scheduledOn);
        schActivity.setLocalExpiresOn(expiresOn);
        schActivity.setGuid("AAA-BBB-CCC");
        schActivity.setHealthCode("FFF-GGG-HHH");
        schActivity.setPersistent(true);
        schActivity.setMinAppVersion(1);
        schActivity.setMaxAppVersion(3);
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String output = BridgeObjectMapper.get().writeValueAsString(schActivity);
        
        JsonNode node = mapper.readTree(output);
        assertEquals("AAA-BBB-CCC", node.get("guid").asText());
        assertEquals(scheduledOnString, node.get("scheduledOn").asText());
        assertEquals(expiresOnString, node.get("expiresOn").asText());
        assertEquals("scheduled", node.get("status").asText());
        assertEquals("ScheduledActivity", node.get("type").asText());
        assertEquals(1, node.get("minAppVersion").asInt());
        assertEquals(3, node.get("maxAppVersion").asInt());
        assertTrue(node.get("persistent").asBoolean());
        
        JsonNode activityNode = node.get("activity");
        assertEquals("Activity3", activityNode.get("label").asText());
        assertEquals("tapTest", activityNode.get("ref").asText());
        assertEquals("task", activityNode.get("activityType").asText());
        assertEquals("Activity", activityNode.get("type").asText());
        
        // zero out the health code field, because that will not be serialized
        schActivity.setHealthCode(null);
        
        DynamoScheduledActivity newActivity = mapper.readValue(output, DynamoScheduledActivity.class);
        // The local schedule values are not serialized and the calculated values aren't deserialized, 
        // but they are verified above.
        newActivity.setTimeZone(DateTimeZone.UTC);
        newActivity.setLocalScheduledOn(scheduledOn);
        newActivity.setLocalExpiresOn(expiresOn);
        
        // Also works without having to reset the timezone.
        assertEquals(schActivity, newActivity);
    }
    
    @Test
    public void hasValidStatusBasedOnTimestamps() throws Exception {
        LocalDateTime now = LocalDateTime.now(DateTimeZone.UTC);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        
        assertEquals(ScheduledActivityStatus.AVAILABLE, schActivity.getStatus());

        schActivity.setLocalScheduledOn(now.plusHours(1));
        assertEquals(ScheduledActivityStatus.SCHEDULED, schActivity.getStatus());
        
        schActivity.setLocalScheduledOn(now.minusHours(3));
        schActivity.setLocalExpiresOn(now.minusHours(1));
        assertEquals(ScheduledActivityStatus.EXPIRED, schActivity.getStatus());
        
        schActivity.setLocalScheduledOn(null);
        schActivity.setLocalExpiresOn(null);
        
        schActivity.setStartedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(ScheduledActivityStatus.STARTED, schActivity.getStatus());
        
        schActivity.setFinishedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(ScheduledActivityStatus.FINISHED, schActivity.getStatus());
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setFinishedOn(DateTime.now().getMillis());
        assertEquals(ScheduledActivityStatus.DELETED, schActivity.getStatus());
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setLocalScheduledOn(now.minusHours(1));
        schActivity.setLocalExpiresOn(now.plusHours(1));
        assertEquals(ScheduledActivityStatus.AVAILABLE, schActivity.getStatus());
    }
    
    /**
     * If a timestamp is not derived from a DateTime value passed into DynamoScheduledActivity, or set 
     * after construction, then the DateTime scheduledOn and expiresOn values are null.
     */
    @Test
    public void dealsTimeZoneAppropriately() {
        DateTime dateTime = DateTime.parse("2010-10-15T00:00:00.001+06:00");
        DateTime dateTimeInZone = DateTime.parse("2010-10-15T00:00:00.001Z");
        
        // Activity with datetime and zone (which is different)
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        // Without a time zone, getStatus() works
        assertEquals(ScheduledActivityStatus.AVAILABLE, schActivity.getStatus());
        // Now set some values
        schActivity.setScheduledOn(dateTime);
        schActivity.setTimeZone(DateTimeZone.UTC);
        
        // Scheduled time should be in the time zone that is set
        assertEquals(DateTimeZone.UTC, schActivity.getScheduledOn().getZone());
        // But the datetime does not itself change (this is one way to test this)
        assertEquals(dateTimeInZone.toLocalDateTime(), schActivity.getScheduledOn().toLocalDateTime());
        
        // setting new time zone everything shifts only in zone, not date or time
        DateTimeZone newZone = DateTimeZone.forOffsetHours(3);
        schActivity.setTimeZone(newZone);
        LocalDateTime copy = schActivity.getScheduledOn().toLocalDateTime();
        assertEquals(newZone, schActivity.getScheduledOn().getZone());
        assertEquals(dateTimeInZone.toLocalDateTime(), copy);
    }
    
    @Test
    public void dateTimesConvertedTest() {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(3);
        DateTime now = DateTime.now();
        DateTime then = DateTime.now().minusDays(1);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(timeZone);
        schActivity.setScheduledOn(now);
        schActivity.setExpiresOn(then);
        assertEquals(schActivity.getLocalScheduledOn(), now.toLocalDateTime());
        assertEquals(schActivity.getLocalExpiresOn(), then.toLocalDateTime());
        
        LocalDateTime local1 = LocalDateTime.parse("2010-01-01T10:10:10");
        LocalDateTime local2 = LocalDateTime.parse("2010-02-02T10:10:10");
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(timeZone);
        schActivity.setLocalScheduledOn(local1);
        schActivity.setLocalExpiresOn(local2);
        assertEquals(schActivity.getScheduledOn(), local1.toDateTime(timeZone));
        assertEquals(schActivity.getExpiresOn(), local2.toDateTime(timeZone));
    }
    
}
