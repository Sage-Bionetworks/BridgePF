package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

public abstract class TaskScheduler {

    protected final DateTime now; 
    protected final String schedulePlanGuid;
    protected final Schedule schedule;
    
    TaskScheduler(String schedulePlanGuid, Schedule schedule) {
        this.schedulePlanGuid = schedulePlanGuid;
        this.schedule = schedule;
        this.now = DateTime.now();
    }
    
    public abstract List<Task> getTasks(Map<String, DateTime> events, DateTime until);
    
    protected DateTime getScheduledTimeBasedOnEvent(Schedule schedule, Map<String, DateTime> events) {
        // If no event is specified, it's enrollment by default.
        String eventId = schedule.getEventId();
        if (eventId == null) {
            eventId = "enrollment";
        }
        DateTime eventTime = (events == null) ? null : events.get(eventId);
        // An event was specified, but it hasn't happened yet. So no tasks are generated.
        // OR, an event fires, but outside of the window for the schedule, so again, no tasks.
        if (eventTime == null || !isInWindow(schedule, eventTime)) {
            return null;
        }
        if (schedule.getDelay() != null) {
            eventTime = eventTime.plus(schedule.getDelay());
        }
        return eventTime;
    }
    
    protected void addTaskForEachTime(List<Task> tasks, DateTime scheduledTime) {
        if (schedule.getTimes().isEmpty()) {
            // We're using whatever hour/minute/seconds were in the original event.
            addTaskForEachActivityAtTime(tasks, scheduledTime);
        } else {
            for (LocalTime time : schedule.getTimes()) {
                scheduledTime = new DateTime(scheduledTime).withTime(time);
                addTaskForEachActivityAtTime(tasks, scheduledTime);
            }
        }
    }
    
    private void addTaskForEachActivityAtTime(List<Task> tasks, DateTime scheduledTime) {
        // If this time point is outside of the schedule's active window, skip it.
        if (isInWindow(schedule, scheduledTime)) {
            // As long at the tasks are not already expired, add them.
            DateTime expiresOn = getExpiresOn(scheduledTime, schedule);
            //if (expiresOn == null || expiresOn.isAfter(scheduledTime)) {
            if (expiresOn == null || expiresOn.isAfter(now)) {
                for (Activity activity : schedule.getActivities()) {
                    DynamoTask task = new DynamoTask();
                    task.setSchedulePlanGuid(schedulePlanGuid);
                    task.setActivity(activity);
                    task.setScheduledOn(scheduledTime.getMillis());
                    task.setRunKey(BridgeUtils.generateTaskKey(task));
                    task.setGuid(BridgeUtils.generateGuid());
                    if (expiresOn != null) {
                        task.setExpiresOn(expiresOn.getMillis());
                        task.setHidesOn(expiresOn.getMillis());
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
    
    private boolean isInWindow(Schedule schedule, DateTime scheduledTime) {
        DateTime startsOn = schedule.getStartsOn();
        DateTime endsOn = schedule.getEndsOn();
        return (startsOn == null || scheduledTime.isEqual(startsOn) || scheduledTime.isAfter(startsOn)) && 
               (endsOn == null || scheduledTime.isEqual(endsOn) || scheduledTime.isBefore(endsOn));
    }
    
    private DateTime getExpiresOn(DateTime scheduledTime, Schedule schedule) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return scheduledTime.plus(schedule.getExpires());
    }

}
