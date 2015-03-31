package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.google.common.collect.Lists;

public final class Schedule implements BridgeEntity {

    public static final String SCHEDULE_TYPE_NAME = "Schedule";
    
    public static final String LABEL_PROPERTY = "label";
    public static final String SCHEDULE_TYPE_PROPERTY = "scheduleType";
    public static final String EVENT_ID_PROPERTY = "eventId";
    public static final String DELAY_PROPERTY = "delay";
    public static final String FREQUENCY_PROPERTY = "frequency";
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
    private Period frequency;
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
    public void setTimes(List<LocalTime> times) {
        this.times = times;
    }
    public void addTime(LocalTime time) {
        checkNotNull(time);
        this.times.add(time);
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
    public void setStartsOn(DateTime startsOn) {
        this.startsOn = startsOn;
    }
    public DateTime getEndsOn() {
        return endsOn;
    }
    public void setEndsOn(DateTime endsOn) {
        this.endsOn = endsOn;
    }
    public Period getExpires() {
        return expires;
    }
    public void setExpires(Period expires) {
        this.expires = expires;
    }
    public Period getDelay() {
        return delay;
    }
    public void setDelay(Period delay) {
        this.delay = delay;
    }
    public Period getFrequency() {
        return frequency;
    }
    public void setFrequency(Period frequency) {
        this.frequency = frequency;
    }
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    public boolean isScheduleFor(GuidCreatedOnVersionHolder keys) {
        for (Activity activity : activities) {
            if (keys.keysEqual(activity.getGuidCreatedOnVersionHolder())) {
                return true;
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
        result = prime * result + Objects.hashCode(frequency);
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
                && Objects.equals(frequency, other.frequency) && Objects.equals(times, other.times)
                && Objects.equals(delay, other.delay));
    }
    @Override
    public String toString() {
        return String.format("Schedule [label=%s, scheduleType=%s, cronTrigger=%s, startsOn=%s, endsOn=%s, delay=%s, expires=%s, frequency=%s, times=%s, eventId=%s, activities=%s]", 
            label, scheduleType, cronTrigger, startsOn, endsOn, delay, expires, frequency, times, eventId, activities);
    }
}    
