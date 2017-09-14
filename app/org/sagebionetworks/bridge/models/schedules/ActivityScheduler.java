package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

import org.sagebionetworks.bridge.BridgeUtils;

public abstract class ActivityScheduler {
    
    protected final Schedule schedule;
    protected DateTime sequenceEndsOn;
    
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
        // Sequence period is measured from the first timestamp plus the delay, to the end of the period.
        if (schedule.getSequencePeriod() != null) {
            sequenceEndsOn = eventTime.plus(schedule.getSequencePeriod());
        }
        
        return eventTime;
    }
    
    protected void addScheduledActivityForAllTimes(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, DateTime dateTime) {

        if (schedule.getTimes().isEmpty()) {
            DateTime localDateTime = dateTime.withZone(context.getInitialTimeZone());
            addScheduledActivityAtTime(scheduledActivities, plan, context, localDateTime.toLocalDate(), localDateTime.toLocalTime());
        } else {
            for (LocalTime localTime : schedule.getTimes()) {
                addScheduledActivityAtTime(scheduledActivities, plan, context, dateTime.toLocalDate(), localTime);
            }
        }
    }
    
    protected void addScheduledActivityAtTime(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate, LocalTime localTime) {
        
        for (Activity activity : schedule.getActivities()) {
            addScheduledActivityAtTimeForOneActivity(scheduledActivities, plan, context, localDate, localTime,
                    activity);
        }
    }
    
    protected void addScheduledActivityAtTimeForOneActivity(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, LocalDate localDate, LocalTime localTime, Activity activity) {
        
        DateTime localDateTime = localDate.toDateTime(localTime, context.getInitialTimeZone());
        if (isInWindow(localDateTime)) {
            // As long at the activities are not already expired, add them.
            LocalDateTime expiresOn = getExpiresOn(localDate, localTime);
            if (expiresOn == null || expiresOn.isAfter(context.getStartsOn().toLocalDateTime())) {
                ScheduledActivity schActivity = ScheduledActivity.create();
                schActivity.setSchedulePlanGuid(plan.getGuid());
                // Use the time zone of the request, not the initial time zone that is used for event dates
                schActivity.setTimeZone(context.getEndsOn().getZone());
                schActivity.setHealthCode(context.getCriteriaContext().getHealthCode());
                schActivity.setActivity(activity);
                LocalDateTime localScheduledOn = localDate.toLocalDateTime(localTime);
                schActivity.setLocalScheduledOn(localScheduledOn);
                schActivity.setGuid(activity.getGuid() + ":" + localDate.toLocalDateTime(localTime));
                schActivity.setPersistent(activity.isPersistentlyRescheduledBy(schedule));
                schActivity.setReferentGuid(BridgeUtils.createReferentGuidIndex(activity, localDate.toLocalDateTime(localTime)));
                if (expiresOn != null) {
                    schActivity.setLocalExpiresOn(expiresOn);
                }
                scheduledActivities.add(schActivity);
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
        DateTime startsOn = schedule.getStartsOn();
        DateTime endsOn = schedule.getEndsOn();

        return (startsOn == null || scheduledTime.isEqual(startsOn) || scheduledTime.isAfter(startsOn)) && 
               (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
    }
    
    private boolean isBeforeWindowEnd(DateTime scheduledTime) {
        DateTime endsOn = schedule.getEndsOn();
        
        return (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
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
            Iterable<String> eventIds = Schedule.EVENT_ID_SPLITTER.split(eventIdsString.trim());
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

        if (scheduledOnAfterSequencePeriod(context, scheduledTime, scheduledActivities.size())) {
            return false;
        }
        if (scheduledOnAfterEndsOnNoMinimumCount(context, scheduledTime)) {
            return false;
        }
        if (!isBeforeWindowEnd(scheduledTime)) {
            return false;
        }
        boolean boundaryNotMet = scheduledTime.isBefore(context.getEndsOn()) || 
                hasNotMetMinimumCount(context, scheduledActivities.size());
        
        return boundaryNotMet;
    }
    
    private boolean scheduledOnAfterSequencePeriod(ScheduleContext context, DateTime scheduledTime, int currentCount) {
        // Don't know why you'd specify a sequence, then force N number of activities to be returned, but the 
        // minimum activities setting is part of the public API and it would be unexpected if it didn't work.
        
        boolean minMet = context.getMinimumPerSchedule() == 0 || currentCount >= context.getMinimumPerSchedule();
        return sequenceEndsOn != null && !scheduledTime.isBefore(sequenceEndsOn) && minMet;
    }
    
    private boolean scheduledOnAfterEndsOnNoMinimumCount(ScheduleContext context, DateTime scheduledTime) {
        return scheduledTime.isAfter(context.getEndsOn()) && context.getMinimumPerSchedule() == 0;
    }
    
    /**
     * If this is a repeating schedule and a minimum value has been set, test to see if the there are enough tasks 
     * to meet the minimum.
     */
    private boolean hasNotMetMinimumCount(ScheduleContext context, int currentCount) {
        return schedule.getScheduleType() != ScheduleType.ONCE && 
               context.getMinimumPerSchedule() > 0 && 
               currentCount < context.getMinimumPerSchedule();
    }
}
