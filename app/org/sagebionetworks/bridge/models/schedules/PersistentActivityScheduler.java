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
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (Activity activity : schedule.getActivities()) {

            // A persistent schedule can start on any event, typically enrollment, but it also reschedules an 
            // activity whenever that activity is finished. This is implicit and does not need to be configured 
            // when creating a schedule. It's clearer if you don't include this "finished" event, though it 
            // won't break anything if a user does include it in the eventId.
            
            // similar to a safety check in ActivityScheduler.getScheduledTimeBasedOnEvent
            if (schedule.getEventId() == null) {
                schedule.setEventId("enrollment");
            }
            String finishedId = "activity:"+activity.getGuid()+":finished";
            DateTime scheduledTime = getFirstEventDateTime(context, finishedId+"," + schedule.getEventId());
            
            if (scheduledTime != null) {
                scheduledTime = Schedule.eventToMidnight(scheduledTime);
                addScheduledActivityAtTime(scheduledActivities, plan, context, scheduledTime);            
            }
        }
        return scheduledActivities;
    }
}
