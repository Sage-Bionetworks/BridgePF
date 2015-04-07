package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

public interface TaskScheduler {

    /**
     * Get all the tasks for the schedule that start before up to the "until" date and time passed to the method. Tasks
     * will be returned from the event that triggers the schedule (by default, the enrollment date of the user). If the
     * events map contains a later event for the last time the schedule plan generated tasks, schedules will be
     * generated after that date and time.
     * 
     * More information about schedules themselves can be found at:
     * https://sagebionetworks.jira.com/wiki/display/BRIDGE/Schedule
     * 
     * @param events
     * @param startAt
     * @param endsAt
     * @return
     */
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until);

}
