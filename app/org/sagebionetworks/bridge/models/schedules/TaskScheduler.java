package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

public interface TaskScheduler {

    /**
     * Get all the tasks for the schedule that start before the "until" date and time. If the schedule has been used in
     * the past, and the events map contains a startsOn event key for the schedule plan that generated the last tasks,
     * then tasks will only be returned with start times after that time.
     * 
     * @param events
     * @param startAt
     * @param endsAt
     * @return
     */
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until);

}
