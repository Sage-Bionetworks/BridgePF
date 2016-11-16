package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.google.common.collect.Lists;

public abstract class ActivityScheduler {
    
    private static final List<LocalTime> TIMES = Lists.newArrayList(LocalTime.MIDNIGHT);

    protected final Schedule schedule;
    
    ActivityScheduler(Schedule schedule) {
        this.schedule = schedule;
    }
    
    public abstract List<ScheduledActivity> getScheduledActivities(SchedulePlan plan, ScheduleContext context);
    
    protected DateTime getScheduledTimeBasedOnEvent(ScheduleContext context) {
        if (!context.hasEvents()) {
            return null;
        }
        // If no event is specified, it's enrollment by default.
        String eventIdString = schedule.getEventId();
        if (eventIdString == null) {
            eventIdString = "enrollment";
        }
        DateTime eventTime = getFirstEventDateTime(context, eventIdString);

        // An event was specified, but it hasn't happened yet.. So no activities are generated.
        if (eventTime == null) {
            return null;
        }
        if (schedule.getDelay() != null) {
            eventTime = eventTime.plus(schedule.getDelay());
        }
        return eventTime;
    }
    
    protected void addScheduledActivityForAllTimes(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate) {
        
        List<LocalTime> localTimes = (schedule.getTimes().isEmpty()) ? TIMES : schedule.getTimes();
        for (LocalTime localTime : localTimes) {
            addScheduledActivityAtTime(scheduledActivities, plan, context, localDate, localTime);
        }
    }
    
    protected void addScheduledActivityAtTime(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate, LocalTime localTime) {
        
        DateTime scheduledTime = localDate.toDateTime(localTime).withZoneRetainFields(DateTimeZone.UTC);
        if (isInWindow(scheduledTime)) {
            // As long at the activities are not already expired, add them.
            DateTime expiresOn = getExpiresOn(scheduledTime);
            if (expiresOn == null || expiresOn.isAfter(context.getNow())) {
                for (Activity activity : schedule.getActivities()) {
                    ScheduledActivity schActivity = ScheduledActivity.create();
                    schActivity.setSchedulePlanGuid(plan.getGuid());
                    schActivity.setTimeZone(context.getZone());
                    schActivity.setHealthCode(context.getCriteriaContext().getHealthCode());
                    schActivity.setActivity(activity);
                    schActivity.setScheduledOn(localDate.toDateTime(localTime, context.getZone()));
                    schActivity.setGuid(activity.getGuid() + ":" + localDate.toLocalDateTime(localTime).toString());
                    schActivity.setPersistent(activity.isPersistentlyRescheduledBy(schedule));
                    schActivity.setSchedule(schedule);
                    if (expiresOn != null) {
                        schActivity.setExpiresOn(expiresOn);
                    }
                    scheduledActivities.add(schActivity);
                }
            }
        }
    }
    
    protected List<ScheduledActivity> trimScheduledActivities(List<ScheduledActivity> scheduledActivities) {
        int count = (schedule.getScheduleType() == ONCE) ? 
            schedule.getActivities().size() :
            scheduledActivities.size();
        return scheduledActivities.subList(0, Math.min(scheduledActivities.size(), count));
    }
    
    private boolean isInWindow(DateTime scheduledTime) {
        scheduledTime = scheduledTime.withZone(DateTimeZone.UTC);
        DateTime startsOn = schedule.getStartsOn();
        DateTime endsOn = schedule.getEndsOn();

        return (startsOn == null || scheduledTime.isEqual(startsOn) || scheduledTime.isAfter(startsOn)) && 
               (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
    }
    
    private boolean isBeforeWindowEnd(DateTime scheduledTime) {
        DateTime endsOn = schedule.getEndsOn();
        
        return (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
    }
    
    private DateTime getExpiresOn(DateTime scheduledTime) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return scheduledTime.plus(schedule.getExpires());
    }

    protected DateTime getFirstEventDateTime(ScheduleContext context, String eventIdsString) {
        DateTime eventDateTime = null;
        if (eventIdsString != null) {
            String[] eventIds = eventIdsString.trim().split("\\s*,\\s*");
            for (String thisEventId : eventIds) {
                if (context.getEvent(thisEventId) != null) {
                    eventDateTime = context.getEvent(thisEventId);
                    break;
                }
            }
        }
        return eventDateTime;
    }
    
    /**
     * If scheduling hasn't reached the end time, or hasn't accumulated the minimum number of tasks, returns true, or 
     * false otherwise. 
     */
    protected boolean shouldContinueScheduling(ScheduleContext context, DateTime scheduledTime,
            List<ScheduledActivity> scheduledActivities) {
        
        boolean boundaryNotMet = scheduledTime.withZoneRetainFields(context.getZone()).isBefore(context.getEndsOn()) || 
                hasNotMetMinimumCount(context, scheduledActivities.size());
        
        return isBeforeWindowEnd(scheduledTime) && boundaryNotMet;
    }
    
    /**
     * If this is a repeating schedule and a minimum value has been set, test to see if the there are enough tasks 
     * to meet the minimum.
     */
    protected boolean hasNotMetMinimumCount(ScheduleContext context, int currentCount) {
        return schedule.getScheduleType() != ScheduleType.ONCE && 
               context.getMinimumPerSchedule() > 0 && 
               currentCount < context.getMinimumPerSchedule();
    }
}
