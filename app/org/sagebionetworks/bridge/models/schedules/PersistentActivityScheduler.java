package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;

public class PersistentActivityScheduler extends ActivityScheduler {

    PersistentActivityScheduler(Schedule schedule) {
        super(schedule);
    }
    
    @Override
    public List<ScheduledActivity> getScheduledActivities(SchedulePlan plan, ScheduleContext context) {
        // similar to a safety check in ActivityScheduler.getScheduleWindowsBasedOnEvents
        if (schedule.getEventId() == null) {
            schedule.setEventId("enrollment");
        }
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (Activity activity : schedule.getActivities()) {

            // A persistent schedule can start on any event, typically enrollment, but it also reschedules an 
            // activity whenever that activity is finished. This is implicit and does not need to be configured 
            // when creating a schedule. It's clearer if you don't include this "finished" event, though it 
            // won't break anything if a user does include it in the eventId.
            String finishedId = "activity:"+activity.getGuid()+":finished";
            List<DateTime> scheduledTimeList = getEventDateTimes(context,
                    finishedId+"," + schedule.getEventId(), false);

            if (!scheduledTimeList.isEmpty()) {
                DateTime scheduledTime = scheduledTimeList.get(0);
                DateTime localDateTime = scheduledTime.withZone(context.getInitialTimeZone());
                
                addScheduledActivityAtTimeForOneActivity(scheduledActivities, plan, context,
                        localDateTime.toLocalDate(), localDateTime.toLocalTime(), activity);
            }
        }
        return scheduledActivities;
    }
}
