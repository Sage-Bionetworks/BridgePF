package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DynamoScheduledActivityTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoScheduledActivity.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void testComparator() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setTimeZone(DateTimeZone.UTC);
        activity1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        activity1.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // Definitely later
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity2.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // The same as 2 in all respects but activity label comes earlier in alphabet
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        assertEquals(activity1, activities.get(0));
        assertEquals(activity3, activities.get(1));
        assertEquals(activity2, activities.get(2));
    }
    
    @Test
    public void handlesNullFieldsReasonably() {
        // No time zone
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        activity1.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // scheduledOn
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // This one is okay
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        // Activity 3 comes first because it's complete, the others follow. This is arbitrary...
        // in reality they are broken activities, but the comparator will not fail.
        assertEquals(activity3, activities.get(0));
        assertEquals(activity1, activities.get(1));
        assertEquals(activity2, activities.get(2));
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
        String output = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(schActivity);
        
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
        assertEquals("tapTest", activityNode.get("task").get("identifier").asText());
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
    
    @Test
    public void serializesCorrectlyToPublicAPI() throws Exception {
        DynamoScheduledActivity act = new DynamoScheduledActivity();
        act.setTimeZone(DateTimeZone.forOffsetHours(-6));
        act.setLocalScheduledOn(LocalDateTime.parse("2015-10-01T10:10:10.000"));
        act.setLocalExpiresOn(LocalDateTime.parse("2015-10-01T14:10:10.000"));
        act.setHidesOn(DateTime.parse("2015-10-01T14:10:10.000-06:00").getMillis());
        act.setRunKey("runKey");
        act.setHealthCode("healthCode");
        act.setGuid("activityGuid");
        act.setSchedulePlanGuid("schedulePlanGuid");
        act.setActivity(TestConstants.TEST_1_ACTIVITY);
        act.setStartedOn(DateTime.parse("2015-10-10T08:08:08.000Z").getMillis());
        act.setFinishedOn(DateTime.parse("2015-12-05T08:08:08.000Z").getMillis());
        act.setPersistent(true);
        act.setMinAppVersion(1);
        act.setMaxAppVersion(2);
        
        String json = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(act);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("activityGuid", node.get("guid").asText());
        assertEquals("2015-10-10T08:08:08.000Z", node.get("startedOn").asText());
        assertEquals("2015-12-05T08:08:08.000Z", node.get("finishedOn").asText());
        assertEquals("true", node.get("persistent").asText());
        assertEquals("1", node.get("minAppVersion").asText());
        assertEquals("2", node.get("maxAppVersion").asText());
        assertEquals("finished", node.get("status").asText());
        assertEquals("ScheduledActivity", node.get("type").asText());
        assertEquals("2015-10-01T10:10:10.000-06:00", node.get("scheduledOn").asText());
        assertEquals("2015-10-01T14:10:10.000-06:00", node.get("expiresOn").asText());
        // all the above, plus activity, and nothing else
        assertEquals(11, TestUtils.getFieldNamesSet(node).size());

        JsonNode activityNode = node.get("activity");
        assertEquals("Activity1", activityNode.get("label").asText());
        assertNotNull(activityNode.get("guid").asText());
        assertEquals("survey", activityNode.get("activityType").asText());
        assertEquals("Activity", activityNode.get("type").asText());
        // all the above, plus survey, and nothing else
        assertEquals(5, TestUtils.getFieldNamesSet(activityNode).size());
        
        JsonNode surveyNode = activityNode.get("survey");
        assertEquals("identifier1", surveyNode.get("identifier").asText());
        assertEquals("AAA", surveyNode.get("guid").asText());
        assertNotNull("href", surveyNode.get("href").asText());
        assertEquals("SurveyReference", surveyNode.get("type").asText());
        // all the above and nothing else
        assertEquals(4, TestUtils.getFieldNamesSet(surveyNode).size());
        
        // Were you to set scheduledOn/expiresOn directly, rather than time zone + local variants,
        // it would still preserve the timezone, that is, the time zone you set separately, not the 
        // time zone you specify.
        act.setScheduledOn(DateTime.parse("2015-10-01T10:10:10.000-05:00"));
        act.setExpiresOn(DateTime.parse("2015-10-01T14:10:10.000-05:00"));
        json = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(act);
        node = BridgeObjectMapper.get().readTree(json);
        // Still in time zone -6 hours.
        assertEquals("2015-10-01T10:10:10.000-06:00", node.get("scheduledOn").asText());
        assertEquals("2015-10-01T14:10:10.000-06:00", node.get("expiresOn").asText());
    }
    
}
