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
 */
public class TaskScheduler {

    private CronExpression cronExpression;
    private Schedule schedule;
    private DateTime executionTime;
    private DateTime referenceTime;
    
    private int count;
    private List<Task> tasks;
    
    public TaskScheduler(Schedule schedule, Map<String,DateTime> events, int count) {
        this.tasks = Lists.newArrayList();
        this.count = (schedule.getScheduleType() == ScheduleType.ONCE) ? 
            schedule.getActivities().size() : count;
        this.schedule = schedule;
        this.executionTime = events.get("now");
        if (schedule.getEventId() == null) {
            schedule.setEventId("enrollment");
        }
        this.referenceTime = events.get(schedule.getEventId());

        if (schedule.getCronTrigger() != null) {
            try {
                this.cronExpression = new CronExpression(schedule.getCronTrigger());    
            } catch(ParseException e) {
                throw new RuntimeException(e);
            }
        }
        checkNotNull(schedule);
        checkNotNull(executionTime);
        // This is expected if the schedule provides an eventId, but that event has never occurred
        //checkNotNull(referenceTime); 
    }
    
    public synchronized List<Task> getTasks() throws ParseException {
        if (!tasks.isEmpty() || referenceTime == null) {
            return tasks;
        }
        if (schedule.getDelay() != null) {
            this.referenceTime = this.referenceTime.plus(schedule.getDelay());
        }
        int limit = 0;
        do {
            addTaskForEachTimeAndActivity();
            advanceSchedule();
        } while (tasks.size() < count && limit++ < (count*2));
        return tasks;
    }
    
    private void addTask(Activity activity) {
        TaskBuilder builder = new TaskBuilder();
        Task task = builder.withStartsOn(referenceTime).withEndsOn(getEndsOn(referenceTime, schedule))
                        .withGuid(BridgeUtils.generateGuid()).withActivity(activity).build();
        if (isInWindow(task) && isStillActive(task) && tasks.size() < count) {
            tasks.add(task);
        }
    }

    private boolean isInWindow(Task task) {
        return (schedule.getStartsOn() == null || task.getStartsOn().isAfter(schedule.getStartsOn())) &&
               (schedule.getEndsOn() == null || task.getStartsOn().isBefore(schedule.getEndsOn()));
    }
    
    private boolean isStillActive(Task task) {
        return task.getEndsOn() == null || task.getEndsOn().isAfter(executionTime);
    }
    
    protected void addTaskForEachTimeAndActivity() {
        // We're using whatever hour/minute/seconds were in the original event.
        if (schedule.getTimes().isEmpty()) {
            for (Activity activity : schedule.getActivities()) {
                addTask(activity);    
            }
        } else {
            for (LocalTime time : schedule.getTimes()) {
                referenceTime = new DateTime(referenceTime).withTime(time);
                for (Activity activity : schedule.getActivities()) {
                    addTask(activity);    
                }
            }
        }
    }
    
    private void advanceSchedule() {
        if (schedule.getInterval() != null) {
            referenceTime = referenceTime.plus(schedule.getInterval());    
        } else if (cronExpression != null) {
            Date next = cronExpression.getNextValidTimeAfter(referenceTime.toDate());
            referenceTime = new DateTime(next, referenceTime.getChronology());
        }
    }
    
    private DateTime getEndsOn(DateTime startsOn, Schedule schedule) {
        if (schedule.getExpires() == null) {
            return null;
        }
        return startsOn.plus(schedule.getExpires());
    }

}
