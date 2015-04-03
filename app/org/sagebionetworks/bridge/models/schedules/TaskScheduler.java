package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.quartz.CronExpression;
import org.sagebionetworks.bridge.BridgeUtils;

import com.google.common.collect.Lists;

/**
 * See: https://sagebionetworks.jira.com/wiki/display/BRIDGE/Schedule
 * 
 * Not thread-safe. 
 * 
 */
public class TaskScheduler {

    private final CronExpression cronExpression;
    private final Schedule schedule;
    private final DateTime executionTime;
    private final DateTime endOfSchedulingWindow;
    /**
     * Although the map is final, you can change its contents and we do this for tests.
     */
    private final Map<String, DateTime> events;
    
    public TaskScheduler(Schedule schedule, Map<String,DateTime> events) {
        this.events = events;
        this.schedule = schedule;
        this.executionTime = events.get("now");
        this.endOfSchedulingWindow = executionTime.plusDays(30); // 1 month. Arguably too long.
        this.cronExpression = parseCronTrigger(schedule.getCronTrigger());
        
        checkNotNull(schedule);
        checkNotNull(executionTime);
        // This is expected if the schedule provides an eventId, but that event has never occurred
        //checkNotNull(referenceTime); 
    }
    
    public synchronized List<Task> getTasks(int count) {
        String eventId = (schedule.getEventId() == null) ? "enrollment" : schedule.getEventId();
        DateTime scheduleTime = events.get(eventId);
        
        List<Task> tasks = Lists.newArrayList();
        if (scheduleTime == null) {
            return tasks;
        }
        if (schedule.getDelay() != null) {
            scheduleTime = scheduleTime.plus(schedule.getDelay());
        }
        int limit = (count*2);
        do {
            scheduleTime = addTaskForEachTimeAndActivity(tasks, scheduleTime);
            scheduleTime = advanceSchedule(scheduleTime);
        } while (--limit > 0 && scheduleTime.isBefore(endOfSchedulingWindow));
        
        return tasks.subList(0,  count);
    }
    
    private CronExpression parseCronTrigger(String cronTrigger) {
        if (cronTrigger != null) {
            try {
                return new CronExpression(schedule.getCronTrigger());    
            } catch(ParseException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
    
    private void addTask(List<Task> tasks, DateTime scheduleTime, Activity activity) {
        TaskBuilder builder = new TaskBuilder();
        Task task = builder
            .withStartsOn(scheduleTime)
            .withEndsOn(getEndsOn(scheduleTime, schedule))
            .withGuid(BridgeUtils.generateGuid())
            .withActivity(activity).build();

        if (isStillActive(task) && isInScheduleWindow(task)) {
            tasks.add(task);
        }
    }

    private boolean isInScheduleWindow(Task task) {
        return (schedule.getStartsOn() == null || task.getStartsOn().isAfter(schedule.getStartsOn())) &&
               (schedule.getEndsOn() == null || task.getStartsOn().isBefore(schedule.getEndsOn()));
    }
    
    private boolean isStillActive(Task task) {
        //return task.getEndsOn() == null || task.getEndsOn().isAfter(executionTime);
        return (task.getStartsOn().isAfter(executionTime) || task.getEndsOn() == null || task.getEndsOn().isAfter(executionTime));
    }
    
    protected DateTime addTaskForEachTimeAndActivity(List<Task> tasks, DateTime scheduleTime) {
        // We're using whatever hour/minute/seconds were in the original event.
        if (schedule.getTimes().isEmpty()) {
            for (Activity activity : schedule.getActivities()) {
                addTask(tasks, scheduleTime, activity);
            }
        } else {
            for (LocalTime time : schedule.getTimes()) {
                scheduleTime = new DateTime(scheduleTime).withTime(time);
                for (Activity activity : schedule.getActivities()) {
                    addTask(tasks, scheduleTime, activity);
                }
            }
        }
        return scheduleTime;
    }
    
    private DateTime advanceSchedule(DateTime scheduleTime) {
        if (schedule.getInterval() != null) {
            return scheduleTime.plus(schedule.getInterval());    
        } else if (cronExpression != null) {
            Date next = cronExpression.getNextValidTimeAfter(scheduleTime.toDate());
            return new DateTime(next, scheduleTime.getChronology());
        }
        return scheduleTime;
    }
    
    private DateTime getEndsOn(DateTime startsOn, Schedule schedule) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return startsOn.plus(schedule.getExpires());
    }

}
