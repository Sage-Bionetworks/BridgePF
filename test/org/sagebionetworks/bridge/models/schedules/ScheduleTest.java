package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.google.common.collect.Lists;

public class ScheduleTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Schedule.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canRountripSerialize() throws Exception {
        Activity activity = new Activity("label", "http://ref/");
        
        Schedule schedule = new Schedule();
        schedule.getActivities().add(activity);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.setDelay(Period.parse("P1D"));
        schedule.setExpires(Period.parse("P2D"));
        schedule.setStartsOn(DateTime.parse("2015-02-02T10:10:10.000Z"));
        schedule.setEndsOn(DateTime.parse("2015-01-01T10:10:10.000Z"));
        schedule.setEventId("eventId");
        schedule.setInterval(Period.parse("P3D"));
        schedule.setLabel("label");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("10:10"), LocalTime.parse("14:00")));
        
        String string = BridgeObjectMapper.get().writeValueAsString(schedule);
        // NOTE: The time values are overly precise. May be possible to adjust formatting of Jackson's Joda Time module.
        assertEquals("{\"label\":\"label\",\"scheduleType\":\"recurring\",\"eventId\":\"eventId\",\"cronTrigger\":\"0 0 8 ? * TUE *\",\"activities\":[{\"label\":\"label\",\"ref\":\"http://ref/\",\"activityType\":\"task\",\"type\":\"Activity\"}],\"delay\":\"P1D\",\"interval\":\"P3D\",\"expires\":\"P2D\",\"startsOn\":\"2015-02-02T10:10:10.000Z\",\"endsOn\":\"2015-01-01T10:10:10.000Z\",\"times\":[\"10:10:00.000\",\"14:00:00.000\"],\"type\":\"Schedule\"}", string);
        schedule = BridgeObjectMapper.get().readValue(string, Schedule.class);
        
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
        activity = schedule.getActivities().get(0);
        assertEquals("label", activity.getLabel());
        assertEquals("http://ref/", activity.getRef());
    }
}
