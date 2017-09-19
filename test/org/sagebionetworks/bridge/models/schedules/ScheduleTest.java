package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class ScheduleTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Schedule.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canRountripSerialize() throws Exception {
        Activity activity = new Activity.Builder().withLabel("label").withTask("ref").build();
        
        Schedule schedule = new Schedule();
        schedule.getActivities().add(activity);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.setDelay(Period.parse("P1D"));
        schedule.setExpires(Period.parse("P2D"));
        schedule.setStartsOn(DateTime.parse("2015-02-02T10:10:10.000Z"));
        schedule.setEndsOn(DateTime.parse("2015-01-01T10:10:10.000Z"));
        schedule.setEventId(Schedule.EVENT_ID_PROPERTY);
        schedule.setInterval(Period.parse("P3D"));
        schedule.setSequencePeriod(Period.parse("P3W"));
        schedule.setLabel(Schedule.LABEL_PROPERTY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("10:10"), LocalTime.parse("14:00")));
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String string = mapper.writeValueAsString(schedule);

        JsonNode node = mapper.readTree(string);
        assertEquals("label", node.get(Schedule.LABEL_PROPERTY).textValue());
        assertEquals("recurring", node.get(Schedule.SCHEDULE_TYPE_PROPERTY).textValue());
        assertEquals("eventId", node.get(Schedule.EVENT_ID_PROPERTY).textValue());
        assertEquals("0 0 8 ? * TUE *", node.get(Schedule.CRON_TRIGGER_PROPERTY).textValue());
        assertEquals("P1D", node.get(Schedule.DELAY_PROPERTY).textValue());
        assertEquals("P3D", node.get(Schedule.INTERVAL_PROPERTY).textValue());
        assertEquals("P2D", node.get(Schedule.EXPIRES_PROPERTY).textValue());
        assertEquals("P3W", node.get(Schedule.SEQUENCE_PERIOD_PROPERTY).textValue());
        assertEquals("2015-02-02T10:10:10.000Z", node.get(Schedule.STARTS_ON_PROPERTY).textValue());
        assertEquals("2015-01-01T10:10:10.000Z", node.get(Schedule.ENDS_ON_PROPERTY).textValue());
        assertFalse(node.get(Schedule.PERSISTENT_PROPERTY).booleanValue());
        assertEquals("Schedule", node.get("type").textValue());

        ArrayNode times = (ArrayNode)node.get("times");
        assertEquals("10:10:00.000", times.get(0).textValue());
        assertEquals("14:00:00.000", times.get(1).textValue());
        
        JsonNode actNode = node.get("activities").get(0);
        assertEquals("label", actNode.get("label").textValue());
        assertEquals("task", actNode.get("activityType").textValue());
        assertEquals("Activity", actNode.get("type").textValue());

        JsonNode taskNode = actNode.get("task");
        assertEquals("ref", taskNode.get("identifier").textValue());
        assertEquals("TaskReference", taskNode.get("type").textValue());
        
        schedule = mapper.readValue(string, Schedule.class);
        assertEquals("0 0 8 ? * TUE *", schedule.getCronTrigger());
        assertEquals("P1D", schedule.getDelay().toString());
        assertEquals("P2D", schedule.getExpires().toString());
        assertEquals("eventId", schedule.getEventId());
        assertEquals("label", schedule.getLabel());
        assertEquals("P3D", schedule.getInterval().toString());
        assertEquals(ScheduleType.RECURRING, schedule.getScheduleType());
        assertEquals("2015-02-02T10:10:10.000Z", schedule.getStartsOn().toString());
        assertEquals("2015-01-01T10:10:10.000Z", schedule.getEndsOn().toString());
        assertEquals("10:10:00.000", schedule.getTimes().get(0).toString());
        assertEquals("14:00:00.000", schedule.getTimes().get(1).toString());
        assertEquals("P3W", schedule.getSequencePeriod().toString());
        activity = schedule.getActivities().get(0);
        assertEquals("label", activity.getLabel());
        assertEquals("ref", activity.getTask().getIdentifier());
    }
    
    @Test
    public void testStringSetters() {
        DateTime date = DateTime.parse("2015-02-02T10:10:10.000Z");
        Period period = Period.parse("P1D");
        Schedule schedule = new Schedule();
        schedule.setDelay("P1D");
        schedule.setEndsOn("2015-02-02T10:10:10.000Z");
        schedule.setStartsOn("2015-02-02T10:10:10.000Z");
        schedule.setExpires("P1D");
        schedule.setInterval("P1D");
        schedule.setSequencePeriod("P1D");
        schedule.addTimes("10:10");
        schedule.addTimes("12:10");
        
        assertEquals(period, schedule.getDelay());
        assertEquals(date, schedule.getEndsOn());
        assertEquals(date, schedule.getStartsOn());
        assertEquals(period, schedule.getExpires());
        assertEquals(period, schedule.getInterval());
        assertEquals(period, schedule.getSequencePeriod());
        assertEquals(Lists.newArrayList(LocalTime.parse("10:10"), LocalTime.parse("12:10")), schedule.getTimes());
    }
    
    @Test
    public void scheduleIdentifiesWhenItIsPersistent() {
        Schedule schedule = new Schedule();
        assertFalse(schedule.getPersistent()); // safe to do this before anything else.
        
        Survey survey = new TestSurvey(ScheduleTest.class, false);
        Activity activity = new Activity.Builder().withLabel("Test").withSurvey(survey.getIdentifier(),
                        survey.getGuid(), new DateTime(survey.getCreatedOn())).build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("survey:"+survey.getGuid()+":finished,enrollment");
        assertTrue(schedule.getPersistent());
        
        schedule.setScheduleType(ScheduleType.RECURRING);
        assertFalse(schedule.getPersistent());
        
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId(null);
        assertFalse(schedule.getPersistent());
        
        schedule = new Schedule();
        activity = new Activity.Builder().withLabel("Test").withTask("BBB").build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("enrollment,task:BBB:finished");
        assertTrue(schedule.getPersistent());
    }
    
    @Test
    public void scheduleWithDelayNotPersistent() {
        Schedule schedule = new Schedule();
        Survey survey = new TestSurvey(ScheduleTest.class, false);
        Activity activity = new Activity.Builder().withLabel("Test").withSurvey(survey.getIdentifier(),
                        survey.getGuid(), new DateTime(survey.getCreatedOn())).build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("survey:"+survey.getGuid()+":finished,enrollment");
        
        schedule.setDelay("P1D");
        assertFalse(schedule.getPersistent());
        
        schedule.setDelay("P1M");
        assertFalse(schedule.getPersistent());
        
        schedule.setDelay("P0D");
        assertTrue(schedule.getPersistent());
        
        schedule.setDelay((Period)null);
        assertTrue(schedule.getPersistent());
    }
}
