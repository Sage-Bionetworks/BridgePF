package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.BridgeUtils;

abstract class TaskScheduler {

    protected final String schedulePlanGuid;
    protected final Schedule schedule;
    
    TaskScheduler(String schedulePlanGuid, Schedule schedule) {
        this.schedulePlanGuid = schedulePlanGuid;
        this.schedule = schedule;
    }
    
    public abstract List<Task> getTasks(Map<String, DateTime> events, DateTime until);
    
    protected DateTime getScheduledTimeBasedOnEvent(Schedule schedule, Map<String, DateTime> events) {
        // If no event is specified, it's enrollment by default.
        String eventId = schedule.getEventId();
        if (eventId == null) {
            eventId = "enrollment";
        }
        DateTime eventTime = events.get(eventId);
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
        if (isInWindow(schedule, scheduledTime)) {
            for (Activity activity : schedule.getActivities()) {
                Task task = new Task(BridgeUtils.generateGuid(), schedulePlanGuid, activity, scheduledTime, getExpiresOn(scheduledTime, schedule));
                tasks.add(task);
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
    
    private DateTime getExpiresOn(DateTime startsOn, Schedule scheduledTime) {
        if (scheduledTime.getExpires() == null) {
            return null;
        }
        return startsOn.plus(scheduledTime.getExpires());
    }

}
