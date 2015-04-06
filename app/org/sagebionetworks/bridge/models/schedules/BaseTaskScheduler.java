package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.BridgeUtils;

abstract class BaseTaskScheduler implements TaskScheduler {

    protected final String schedulePlanGuid;
    protected final Schedule schedule;
    
    BaseTaskScheduler(String schedulePlanGuid, Schedule schedule) {
        this.schedulePlanGuid = schedulePlanGuid;
        this.schedule = schedule;
    }
    
    protected DateTime getStartTimeBasedOnEvent(Schedule schedule, Map<String, DateTime> events) {
        // If no event is specified, it's enrollment by default.
        String eventId = schedule.getEventId();
        if (eventId == null) {
            eventId = "enrollment";
        }
        DateTime event = events.get(eventId);
        // An event was specified, but it hasn't happened yet. So no tasks are generated.
        // OR, an event fires, but outside of the window for the schedule, so again, no tasks.
        if (event == null || !isInWindow(schedule, event)) {
            throw new IllegalStateException();
        }
        if (schedule.getDelay() != null) {
            event = event.plus(schedule.getDelay());
        }
        return event;
    }
    
    protected void addTaskForEachTime(List<Task> tasks, DateTime datetime) {
        if (schedule.getTimes().isEmpty()) {
            // We're using whatever hour/minute/seconds were in the original event.
            addTaskForEachActivityAtTime(tasks, datetime);
        } else {
            for (LocalTime time : schedule.getTimes()) {
                datetime = new DateTime(datetime).withTime(time);
                addTaskForEachActivityAtTime(tasks, datetime);
            }
        }
    }
    
    private void addTaskForEachActivityAtTime(List<Task> tasks, DateTime datetime) {
        // This task falls outside of the scheduling window. Stop creating tasks.
        if (isAfterEnd(schedule, datetime)) {
            throw new IllegalStateException();
        }
        if (isInWindow(schedule, datetime)) {
            for (Activity activity : schedule.getActivities()) {
                Task task = new Task(BridgeUtils.generateGuid(), schedulePlanGuid, activity, datetime, getEndsOn(datetime, schedule));
                tasks.add(task);
            }
        }
    }
    
    protected List<Task> trimTasks(List<Task> tasks) {
        int count = (schedule.getScheduleType() == ScheduleType.ONCE) ? 1 : tasks.size();
        return tasks.subList(0, Math.min(tasks.size(), count));
    }
    
    private boolean isAfterEnd(Schedule schedule, DateTime datetime) {
        DateTime endsOn = schedule.getEndsOn();
        return (endsOn != null && datetime.isAfter(endsOn));
    }
    
    private boolean isInWindow(Schedule schedule, DateTime datetime) {
        DateTime startsOn = schedule.getStartsOn();
        DateTime endsOn = schedule.getEndsOn();
        return (startsOn == null || datetime.isEqual(startsOn) || datetime.isAfter(startsOn)) && 
               (endsOn == null || datetime.isEqual(endsOn) || datetime.isBefore(endsOn));
    }
    
    private DateTime getEndsOn(DateTime startsOn, Schedule schedule) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return startsOn.plus(schedule.getExpires());
    }

}
