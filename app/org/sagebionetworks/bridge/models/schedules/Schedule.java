package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

public final class Schedule implements BridgeEntity {

    public static final String SCHEDULE_TYPE_NAME = "Schedule";
    
    public static final String LABEL_PROPERTY = "label";
    public static final String SCHEDULE_TYPE_PROPERTY = "scheduleType";
    public static final String EVENT_ID_PROPERTY = "eventId";
    public static final String DELAY_PROPERTY = "delay";
    public static final String INTERVAL_PROPERTY = "interval";
    public static final String EXPIRES_PROPERTY = "expires";
    public static final String CRON_TRIGGER_PROPERTY = "cronTrigger";
    public static final String STARTS_ON_PROPERTY = "startsOn";
    public static final String ENDS_ON_PROPERTY = "endsOn";
    public static final String TYPE_PROPERTY_NAME = "type";
    public static final String ACTIVITIES_PROPERTY = "activities";
    public static final String TIMES_PROPERTY = "times";
   
    private String label;
    private ScheduleType scheduleType;
    private String eventId;
    private Period delay;
    private Period interval;
    private Period expires;
    private String cronTrigger;
    private DateTime startsOn;
    private DateTime endsOn;
    private List<LocalTime> times = Lists.newArrayList();
    private List<Activity> activities = Lists.newArrayList();
    
    public List<Activity> getActivities() {
        return activities;
    }
    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }
    public void addActivity(Activity activity) {
        checkNotNull(activity);
        this.activities.add(activity);
    }
    public List<LocalTime> getTimes() {
        return times;
    }
    @JsonProperty("times")
    public void setTimes(List<LocalTime> times) {
        this.times = times;
    }
    public void addTimes(LocalTime... times) {
        for (LocalTime time : times) {
            checkNotNull(time);
            this.times.add(time);
        }
    }
    public void addTimes(String... times) {
        for (String time : times) {
            checkNotNull(time);
            this.times.add(LocalTime.parse(time));
        }
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public ScheduleType getScheduleType() {
        return scheduleType;
    }
    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }
    public String getCronTrigger() {
        return cronTrigger;
    }
    public void setCronTrigger(String cronTrigger) {
        this.cronTrigger = cronTrigger;
    }
    public DateTime getStartsOn() {
        return startsOn;
    }
    @JsonProperty("startsOn")
    public void setStartsOn(DateTime startsOn) {
        this.startsOn = startsOn;
    }
    public void setStartsOn(String startsOn) {
        setStartsOn(DateTime.parse(startsOn));
    }
    public DateTime getEndsOn() {
        return endsOn;
    }
    @JsonProperty("endsOn")
    public void setEndsOn(DateTime endsOn) {
        this.endsOn = endsOn;
    }
    public void setEndsOn(String endsOn) {
        setEndsOn(DateTime.parse(endsOn));
    }
    public Period getExpires() {
        return expires;
    }
    @JsonProperty("expires")
    public void setExpires(Period expires) {
        this.expires = expires;
    }
    public void setExpires(String expires) {
        setExpires(Period.parse(expires));
    }
    public Period getDelay() {
        return delay;
    }
    @JsonProperty("delay")
    public void setDelay(Period delay) {
        this.delay = delay;
    }
    public void setDelay(String delay) {
        setDelay(Period.parse(delay));
    }
    public Period getInterval() {
        return interval;
    }
    @JsonProperty("interval")
    public void setInterval(Period interval) {
        this.interval = interval;
    }
    public void setInterval(String interval) {
        setInterval(Period.parse(interval));
    }
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    /**
     * A persistent schedule is one that keeps a task alive in the list of tasks, 
     * recreating it every time it is completed. Persistent schedules are scheduled to 
     * occur one time, but have an event ID that immediately triggers re-scheduling when 
     * one of the activities assigned by the schedule is completed.
     * @return
     */
    public boolean getPersistent() {
        // It must be scheduled once, triggered against an eventId (and of course, there need to be activities)
        if (activities != null) {
            for (Activity activity : activities) {
                if (activity.isPersistentlyRescheduledBy(this)) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean isScheduleFor(GuidCreatedOnVersionHolder keys) {
        for (Activity activity : activities) {
            SurveyReference reference = activity.getSurvey();
            if (reference != null && reference.getCreatedOn() != null) {
                long createdOn = reference.getCreatedOn().getMillis();    
                if (keys.getGuid().equals(reference.getGuid()) && keys.getCreatedOn() == createdOn) {
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activities);
        result = prime * result + Objects.hashCode(cronTrigger);
        result = prime * result + Objects.hashCode(endsOn);
        result = prime * result + Objects.hashCode(expires);
        result = prime * result + Objects.hashCode(delay);
        result = prime * result + Objects.hashCode(interval);
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(scheduleType);
        result = prime * result + Objects.hashCode(startsOn);
        result = prime * result + Objects.hashCode(eventId);
        result = prime * result + Objects.hashCode(times);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Schedule other = (Schedule) obj;
        return (Objects.equals(activities, other.activities) && Objects.equals(cronTrigger, other.cronTrigger)
                && Objects.equals(endsOn, other.endsOn) && Objects.equals(expires, other.expires)
                && Objects.equals(label, other.label) && Objects.equals(scheduleType, other.scheduleType) 
                && Objects.equals(startsOn, other.startsOn) && Objects.equals(eventId, other.eventId) 
                && Objects.equals(interval, other.interval) && Objects.equals(times, other.times)
                && Objects.equals(delay, other.delay));
    }
    @Override
    public String toString() {
        return String.format("Schedule [label=%s, scheduleType=%s, cronTrigger=%s, startsOn=%s, endsOn=%s, delay=%s, expires=%s, interval=%s, times=%s, eventId=%s, activities=%s]", 
            label, scheduleType, cronTrigger, startsOn, endsOn, delay, expires, interval, times, eventId, activities);
    }
}    
