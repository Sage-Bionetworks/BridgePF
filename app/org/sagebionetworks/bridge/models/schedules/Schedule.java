package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

// This has to be non-final because there's a lot of complex logic in here (such as getScheduler()) that needs to be
// mocked. Long-term, we should probably migrate those functions to a separate class, so we have a clean separation
// between data and functionality, and to enable easier testing.
//
// Making this class non-final should be okay as long as hashCode() and equals() are both final, and equals() uses
// instanceof.
public class Schedule implements BridgeEntity {
    
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
    public static final String SEQUENCE_PERIOD_PROPERTY = "sequencePeriod";
    public static final String PERSISTENT_PROPERTY = "persistent";

    public static final Splitter EVENT_ID_SPLITTER = Splitter.on(Pattern.compile("\\s*,\\s*"));
   
    private String label;
    private ScheduleType scheduleType;
    private String eventId;
    private Period delay;
    private Period interval;
    private Period expires;
    private Period sequencePeriod;
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
    public Period getSequencePeriod() {
        return sequencePeriod;
    }
    public void setSequencePeriod(Period sequencePeriod) {
        this.sequencePeriod = sequencePeriod;
    }
    public void setSequencePeriod(String sequencePeriod) {
        setSequencePeriod(Period.parse(sequencePeriod));
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
     * Persistent schedules will reschedule an activity immediately after it is finished. 
     * This is a type of schedule, but previously it was also inferred from a particular 
     * configuration of one-time schedules. Both kinds of persistent schedule are supported 
     * until we can migrate to the new PERSISTENT schedule type.
     */
    public boolean getPersistent() {
        if (getScheduleType() == ScheduleType.PERSISTENT) {
            return true;
        }
        if (activities != null) {
            for (Activity activity : activities) {
                if (activity.isPersistentlyRescheduledBy(this)) {
                    return true;
                }
            }
        }
        return false;
    }
    @JsonIgnore
    @DynamoDBIgnore
    public ActivityScheduler getScheduler() {
        if (getCronTrigger() != null) {
            return new CronActivityScheduler(this);
        } else if (scheduleType == ScheduleType.PERSISTENT) {
            return new PersistentActivityScheduler(this);
        }
        return new IntervalActivityScheduler(this);
    }
    public boolean schedulesImmediatelyAfterEvent() {
        return getEventId() != null && 
               getScheduleType() == ScheduleType.ONCE &&        
               (getDelay() == null || getDelay().toDurationFrom(DateTime.now()).getMillis() <= 0L);
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
    public final int hashCode() {
        return Objects.hash(activities, cronTrigger, endsOn, expires, sequencePeriod, delay, interval, label,
                scheduleType, startsOn, eventId, times);
    }
    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof Schedule)) {
            return false;
        }
        Schedule other = (Schedule) obj;
        return (Objects.equals(activities, other.activities) && Objects.equals(cronTrigger, other.cronTrigger)
                && Objects.equals(endsOn, other.endsOn) && Objects.equals(expires, other.expires)
                && Objects.equals(sequencePeriod, other.sequencePeriod) && Objects.equals(label, other.label)
                && Objects.equals(scheduleType, other.scheduleType) && Objects.equals(startsOn, other.startsOn)
                && Objects.equals(eventId, other.eventId) && Objects.equals(interval, other.interval)
                && Objects.equals(times, other.times) && Objects.equals(delay, other.delay));
    }
    @Override
    public String toString() {
        return String.format("Schedule [label=%s, scheduleType=%s, cronTrigger=%s, startsOn=%s, endsOn=%s, delay=%s, expires=%s, sequencePeriod=%s, interval=%s, times=%s, eventId=%s, activities=%s]", 
            label, scheduleType, cronTrigger, startsOn, endsOn, delay, expires, sequencePeriod, interval, times, eventId, activities);
    }
}    
