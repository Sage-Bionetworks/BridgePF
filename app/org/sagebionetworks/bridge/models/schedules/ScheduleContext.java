package org.sagebionetworks.bridge.models.schedules;

import java.util.Map;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.collect.ImmutableMap;

/**
 * All the information necessary to convert a schedule into a set of tasks, on a given request. 
 */
public final class ScheduleContext {
    
    private final DateTimeZone zone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final String schedulePlanGuid;
    private final String healthCode;
    
    public ScheduleContext(DateTimeZone zone, DateTime endsOn, String healthCode, Map<String,DateTime> events, String schedulePlanGuid) {
        this.zone = zone;
        // This value is submitted in days, so be sure you're counting all the way through the given day.
        this.endsOn = (endsOn == null) ? null : endsOn.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        this.healthCode = healthCode;
        this.events = (events == null) ? null : ImmutableMap.copyOf(events);
        this.schedulePlanGuid = schedulePlanGuid; 
    }
    
    /**
     * Copy method to copy existing values, augmenting the context with an event map.
     * @param events
     * @return
     */
    public ScheduleContext withEvents(Map<String,DateTime> events) {
        return new ScheduleContext(this.zone, this.endsOn, this.healthCode, events, this.schedulePlanGuid);
    }
    
    /**
     * Copy method to copy existing values, augmenting the context with a schedule plan GUID.
     * @param schedulePlanGuid
     * @return
     */
    public ScheduleContext withSchedulePlan(String schedulePlanGuid) {
        return new ScheduleContext(this.zone, this.endsOn, this.healthCode, this.events, schedulePlanGuid);
    }
    
    /**
     * The time zone of the client at the time of this request. This allows the scheduler to accommodate 
     * moves between time zones.
     * @return
     */
    public DateTimeZone getZone() {
        return zone;
    }
    
    /**
     * The current request is asking for tasks up to a given end date.
     * @return
     */
    public DateTime getEndsOn() {
        return endsOn;
    }

    /**
     * The current user's health code.
     * @return
     */
    public String getHealthCode() {
        return healthCode;
    }
    
    /**
     * The schedule plan providing the schedule, used to keep track of individual runs of the 
     * scheduler to generate a set of tasks.
     * @return
     */
    public String getSchedulePlanGuid() {
        return schedulePlanGuid;
    }
    
    /**
     * Are there any events recorded for this participant? This should always return true since every 
     * participant should have an enrollment event, if nothing else.
     * @return
     */
    public boolean hasEvents() {
        return (events != null && !events.isEmpty());
    }
    
    /**
     * Get an event timestamp for a given event ID.
     * @param eventId
     * @return
     */
    public DateTime getEvent(String eventId) {
        return (events != null) ? events.get(eventId) : null;
    }
    
    /**
     * Returns now in the user's time zone. 
     * @return
     */
    public DateTime getNow() {
        return DateTime.now(zone);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(zone, endsOn, healthCode, events, schedulePlanGuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && Objects.equals(zone, other.zone) &&
                Objects.equals(healthCode, other.healthCode) && Objects.equals(events, other.events) && 
                Objects.equals(schedulePlanGuid, other.schedulePlanGuid));
    }

    @Override
    public String toString() {
        return "ScheduleContext [zone=" + zone + ", endsOn=" + endsOn + ", events=" + events + 
            ", schedulePlanGuid=" + schedulePlanGuid + "]";
    }
}
