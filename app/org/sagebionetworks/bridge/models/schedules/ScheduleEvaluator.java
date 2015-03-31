package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.quartz.CronExpression;

import com.google.common.collect.Lists;

/**
 * The purpose of this class is to validate that the schedule information we are collecting can be turned into discreet
 * scheduled events with start and end dates. It is not used elsewhere in the code.
 * 
 * The evaluator returns a list of tasks according to the schedule. It does not return tasks that have expired (tasks
 * that end after the "now" time of the evaluator run). The evaluator returns when the desired number of tasks have been
 * calculated.
 * 
 * It evaluates from the start of the sequence each time. This may become an issue for long-running studies, but there
 * are ways to short-circuit this behavior as an optimization.
 * 
 * First the delay period is added to the event time, if necessary. Further timestamps are calculated from this offset
 * timestamp.
 * 
 * After this, there are two strategies for scheduling.
 * 
 * The first is cron based. If there is no frequency value but there is a cron expression, then the cron expression will
 * be used from the "event" timestamp forward.
 * 
 * The second scheduling strategy involves a frequency period. If this exists, it is used to schedule from the offset
 * timestamp, and the time of day is taken either from a cron expression (using only the day or less portions of that
 * expression and ignoring the rest), or the original time of day of the event itself. The last is useless, so include a
 * cron expression.
 * 
 * Finally, the end time of the task is always the start timestamp plus the expires period. If there's no expires
 * period, then the task never expires. This is usually not what you want.
 * 
 * When this is ready I will probably rename it to "TaskScheduler".
 * 
 */
public class ScheduleEvaluator {

    private CronExpression cronExpression;
    private Schedule schedule;
    private DateTime executionTime;
    private DateTime referenceTime;
    
    private int count;
    private List<Task> tasks;
    
    public ScheduleEvaluator(Schedule schedule, Map<String,DateTime> events, int count) {
        this.tasks = Lists.newArrayList();
        this.count = (schedule.getScheduleType() == ScheduleType.ONCE) ? 1 : count;
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
        checkNotNull(referenceTime);
    }
    
    public synchronized List<Task> getTasks() throws ParseException {
        if (!tasks.isEmpty()) {
            return tasks;
        }
        if (schedule.getDelay() != null) {
            this.referenceTime = this.referenceTime.plus(schedule.getDelay());
        }
        int limit = 0;
        do {
            addTaskForEachTime();
            advanceSchedule();
        } while (tasks.size() < count && limit++ < (count*2));
        return tasks;
    }
    
    private void addTask() {
        Task task = new Task(referenceTime, getEndsOn(referenceTime, schedule));
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
    
    private void addTaskForEachTime() {
        // We're using whatever hour/minute/seconds were in the original event.
        if (schedule.getTimes().isEmpty()) {
            addTask();
        } else {
            for (LocalTime time : schedule.getTimes()) {
                referenceTime = new DateTime(referenceTime).withTime(time);
                addTask();
            }
        }
    }
    
    private void advanceSchedule() {
        if (schedule.getFrequency() != null) {
            referenceTime = referenceTime.plus(schedule.getFrequency());    
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
