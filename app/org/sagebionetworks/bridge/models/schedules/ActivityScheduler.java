package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

import com.google.common.collect.Lists;

public abstract class ActivityScheduler {
    
    private static final List<LocalTime> MIDNIGHT_IN_LIST = Lists.newArrayList(LocalTime.MIDNIGHT);

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
    
    protected LocalTime addScheduledActivityForAllTimes(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate) {
        
        List<LocalTime> localTimes = (schedule.getTimes().isEmpty()) ? MIDNIGHT_IN_LIST : schedule.getTimes();
        for (LocalTime localTime : localTimes) {
            addScheduledActivityAtTime(scheduledActivities, plan, context, localDate, localTime);
        }
        return localTimes.get(0);
    }
    
    protected boolean addScheduledActivityAtTime(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate, LocalTime localTime) {
        
        if (isValidForScheduling(context, scheduledActivities.size(), localDate, localTime)) {
            LocalDateTime expiresOn = getExpiresOn(localDate, localTime);
            for (Activity activity : schedule.getActivities()) {
                ScheduledActivity schActivity = ScheduledActivity.create();
                schActivity.setSchedulePlanGuid(plan.getGuid());
                schActivity.setTimeZone(context.getZone());
                schActivity.setHealthCode(context.getCriteriaContext().getHealthCode());
                schActivity.setActivity(activity);
                schActivity.setLocalScheduledOn(localDate.toLocalDateTime(localTime));
                schActivity.setGuid(activity.getGuid() + ":" + localDate.toLocalDateTime(localTime));
                schActivity.setPersistent(activity.isPersistentlyRescheduledBy(schedule));
                schActivity.setSchedule(schedule);
                if (expiresOn != null) {
                    schActivity.setLocalExpiresOn(expiresOn);
                }
                scheduledActivities.add(schActivity);
            }
            return true;
        }
        return false;
    }
    
    protected boolean shouldContinueScheduling(ScheduleContext context, DateTime datetime, int activityCount) {
        boolean beforeContextEndsOn = isEqualOrBefore(context.getEndsOn(), datetime);
        boolean beforeEndsOn = isEqualOrBefore(schedule.getEndsOn(), datetime);
        
        return beforeEndsOn && (beforeContextEndsOn || hasNotMetMinimumCount(context, activityCount));
    }
    
    protected boolean isValidForScheduling(ScheduleContext context, int activityCount, LocalDate localDate,
            LocalTime localTime) {

        DateTime localDateTime = localDate.toDateTime(localTime, context.getZone());
        boolean beforeContextEndsOn = shouldContinueScheduling(context, localDateTime, activityCount);

        boolean inTimeWindow = isEqualOrAfter(schedule.getStartsOn(), localDateTime)
                && isEqualOrBefore(schedule.getEndsOn(), localDateTime);

        LocalDateTime expiresOn = getExpiresOn(localDate, localTime);
        boolean notExpired = (expiresOn == null || expiresOn.isAfter(context.getNow().toLocalDateTime()));
        
        return inTimeWindow && beforeContextEndsOn && notExpired;
    }

    protected List<ScheduledActivity> trimScheduledActivities(List<ScheduledActivity> scheduledActivities) {
        int count = (schedule.getScheduleType() == ONCE) ? 
            schedule.getActivities().size() :
            scheduledActivities.size();
        return scheduledActivities.subList(0, Math.min(scheduledActivities.size(), count));
    }
    
    private LocalDateTime getExpiresOn(LocalDate localDate, LocalTime localTime) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return localDate.toLocalDateTime(localTime).plus(schedule.getExpires());
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
     * If this is a repeating schedule and a minimum value has been set, test to see if the there are enough tasks 
     * to meet the minimum.
     */
    protected boolean hasNotMetMinimumCount(ScheduleContext context, int currentCount) {
        return schedule.getScheduleType() != ScheduleType.ONCE && 
               context.getMinimumPerSchedule() > 0 && 
               currentCount < context.getMinimumPerSchedule();
    }
    
    protected boolean isEqualOrBefore(DateTime target, DateTime value) {
        return (target == null || value.isEqual(target) || value.isBefore(target));
    }

    protected boolean isEqualOrAfter(DateTime target, DateTime value) {
        return (target == null || value.isEqual(target) || value.isAfter(target));
    }
}
