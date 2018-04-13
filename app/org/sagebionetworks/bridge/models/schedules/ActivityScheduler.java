package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.RangeTuple;

public abstract class ActivityScheduler {
    
    protected final Schedule schedule;

    ActivityScheduler(Schedule schedule) {
        this.schedule = schedule;
    }
    
    public abstract List<ScheduledActivity> getScheduledActivities(SchedulePlan plan, ScheduleContext context);

    /**
     * Given the schedule context and the schedule, return a list of start/end time windows in which we should
     * schedule events. In each window, start time the time we should start scheduling after. End time is the time we
     * should stop scheduling the event. If end time is not specified, we schedule the event indefinitely.
     */
    protected List<RangeTuple<DateTime>> getScheduleWindowsBasedOnEvents(ScheduleContext context) {
        if (!context.hasEvents()) {
            // Short-cut: If there are no events in the event map, return an empty list.
            return ImmutableList.of();
        }

        // If no event is specified, it's enrollment by default.
        String eventIdString = schedule.getEventId();
        if (eventIdString == null) {
            eventIdString = "enrollment";
        }

        // For one-time and persistent schedules, schedule off the first event specified in the list. For recurring
        // schedules, schedule off _all_ events specified.
        boolean getAll = schedule.getScheduleType() == ScheduleType.RECURRING;
        List<DateTime> eventTimeList = getEventDateTimes(context, eventIdString, getAll);

        List<RangeTuple<DateTime>> scheduleWindowList = new ArrayList<>();
        for (DateTime oneEventTime : eventTimeList) {
            // Start time is event time, with added delay if it's present.
            DateTime startTime = oneEventTime;
            if (schedule.getDelay() != null) {
                startTime = oneEventTime.plus(schedule.getDelay());
            }

            // End time is only present if a sequence period is specified.
            // Sequence period is measured from the first timestamp plus the delay, to the end of the period.
            DateTime endTime = null;
            if (schedule.getSequencePeriod() != null) {
                endTime = startTime.plus(schedule.getSequencePeriod());
            }

            // Construct the range tuple.
            scheduleWindowList.add(new RangeTuple<>(startTime, endTime));
        }
        
        return scheduleWindowList;
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

    /**
     * Helper method used to get specified event date times from the schedule context.
     *
     * @param context
     *         schedule context, which contains the event map
     * @param eventIdsString
     *         event ID string as specified by the schedule; this is a comma-delimited list
     * @param getAll
     *         true to get all events; false to just get any one event (generally useful for persistent activities)
     * @return list of one or all specified event date-times
     */
    protected List<DateTime> getEventDateTimes(ScheduleContext context, String eventIdsString, boolean getAll) {
        List<DateTime> eventDateTimeList = new ArrayList<>();
        if (eventIdsString != null) {
            Iterable<String> eventIds = Schedule.EVENT_ID_SPLITTER.split(eventIdsString.trim());
            for (String thisEventId : eventIds) {
                if (context.getEvent(thisEventId) != null) {
                    eventDateTimeList.add(context.getEvent(thisEventId));

                    if (!getAll) {
                        // We only wanted one event, and we found it, so break.
                        break;
                    }
                }
            }
        }
        return eventDateTimeList;
    }

    /**
     * If scheduling hasn't reached the end time, or hasn't accumulated the minimum number of tasks, returns true, or 
     * false otherwise. 
     */
    protected boolean shouldContinueScheduling(ScheduleContext context, DateTime scheduledTime,
            RangeTuple<DateTime> scheduleWindow, List<ScheduledActivity> scheduledActivities) {

        if (scheduledOnAfterSequencePeriod(scheduledTime, scheduleWindow)) {
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
    
    private boolean scheduledOnAfterSequencePeriod(DateTime scheduledTime, RangeTuple<DateTime> scheduleWindow) {
        DateTime sequenceEndsOn = scheduleWindow.getEnd();
        return sequenceEndsOn != null && !scheduledTime.isBefore(sequenceEndsOn);
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
