package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

public abstract class TaskScheduler {

    protected final Schedule schedule;
    
    TaskScheduler(Schedule schedule) {
        this.schedule = schedule;
    }
    
    public abstract List<Task> getTasks(ScheduleContext context);
    
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

        // An event was specified, but it hasn't happened yet. So no tasks are generated.
        // OR, an event fires, but outside of the window for the schedule, so again, no tasks.
        if (eventTime == null || !isInWindow(eventTime)) {
            return null;
        }
        if (schedule.getDelay() != null) {
            eventTime = eventTime.plus(schedule.getDelay());
        }
        return eventTime;
    }
    
    protected void addTaskForEachTime(List<Task> tasks, ScheduleContext context, DateTime scheduledTime) {
        if (schedule.getTimes().isEmpty()) {
            // We're using whatever hour/minute/seconds were in the original event.
            addTaskForEachActivityAtTime(tasks, context, scheduledTime);
        } else {
            for (LocalTime time : schedule.getTimes()) {
                scheduledTime = new DateTime(scheduledTime, context.getZone()).withTime(time);
                addTaskForEachActivityAtTime(tasks, context, scheduledTime);
            }
        }
    }
    
    private void addTaskForEachActivityAtTime(List<Task> tasks, ScheduleContext context, DateTime scheduledTime) {
        // Assert that the scheduledTime was constructed by subclass implementation with the correct time zone.
        checkArgument(context.getZone().equals(scheduledTime.getZone()), 
            "Scheduled DateTime does not have requested time zone: " + scheduledTime.getZone());

        // If this time point is outside of the schedule's active window, skip it.
        if (isInWindow(scheduledTime)) {
            // As long at the tasks are not already expired, add them.
            DateTime expiresOn = getExpiresOn(scheduledTime);
            if (expiresOn == null || expiresOn.isAfter(context.getNow())) {
                for (Activity activity : schedule.getActivities()) {
                    Task task = Task.create();
                    task.setTimeZone(context.getZone());
                    task.setHealthCode(context.getHealthCode());
                    task.setActivity(activity);
                    task.setScheduledOn(scheduledTime);
                    task.setGuid(BridgeUtils.generateGuid());
                    task.setPersistent(activity.isPersistentlyRescheduledBy(schedule));
                    if (expiresOn != null) {
                        task.setExpiresOn(expiresOn);
                        task.setHidesOn(expiresOn.getMillis());
                    }
                    if (context.getSchedulePlanGuid() != null) {
                        task.setRunKey(BridgeUtils.generateTaskRunKey(task, context));
                    }
                    tasks.add(task);
                }
            }
        }
    }
    
    protected List<Task> trimTasks(List<Task> tasks) {
        int count = (schedule.getScheduleType() == ONCE) ? 
            schedule.getActivities().size() :
            tasks.size();
        return tasks.subList(0, Math.min(tasks.size(), count));
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
}
