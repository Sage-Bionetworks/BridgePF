package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

public abstract class ActivityScheduler {

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
    
    protected void addScheduledActivityForAllTimes(List<ScheduledActivity> scheduledActivities, 
            SchedulePlan plan, ScheduleContext context, DateTime scheduledTime) {
        if (schedule.getTimes().isEmpty()) {
            // We're using whatever hour/minute/seconds were in the original event.
            addScheduledActivityAtTime(scheduledActivities, plan, context, scheduledTime);
        } else {
            for (LocalTime time : schedule.getTimes()) {
                scheduledTime = new DateTime(scheduledTime, context.getZone()).withTime(time);
                addScheduledActivityAtTime(scheduledActivities, plan, context, scheduledTime);
            }
        }
    }
    
    private void addScheduledActivityAtTime(List<ScheduledActivity> scheduledActivities, SchedulePlan plan,
            ScheduleContext context, DateTime scheduledTime) {
        // Assert that the scheduledTime was constructed by subclass implementation with the correct time zone.
        checkArgument(context.getZone().equals(scheduledTime.getZone()), 
            "Scheduled DateTime does not have requested time zone: " + scheduledTime.getZone());

        // If this time point is outside of the schedule's active window, skip it.
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
                    schActivity.setScheduledOn(scheduledTime);
                    schActivity.setGuid(activity.getGuid() + ":" + scheduledTime.toLocalDateTime().toString());
                    schActivity.setPersistent(activity.isPersistentlyRescheduledBy(schedule));
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
        DateTime startsOn = schedule.getStartsOn();
        DateTime endsOn = schedule.getEndsOn();
        
        return (startsOn == null || scheduledTime.isEqual(startsOn) || scheduledTime.isAfter(startsOn)) && 
               (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
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
                    eventDateTime = context.getEvent(thisEventId).withZone(context.getZone());
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
    protected boolean shouldContinueScheduling(ScheduleContext context, DateTime scheduledTime, List<ScheduledActivity> scheduledActivities) {
        return scheduledTime.isBefore(context.getEndsOn()) || 
               hasNotMetMinimumCount(context, scheduledActivities.size());
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
