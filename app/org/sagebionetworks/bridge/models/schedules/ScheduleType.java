package org.sagebionetworks.bridge.models.schedules;

/**
 * All schedules are calculated relative to an event timestamp. The implicit timestamp is the date and time that a
 * participant joins a study. The eventId can be used to specify a different timestamp.
 * 
 * Evaluation creates a stable task with a stable starting timestamp, so the system can determine if the task already
 * exists. It's an unsolved problem as to what to do with long-running schedules that require advancing thousands of
 * days to find the next valid recurring task.
 */
public enum ScheduleType {
    ONCE,
    RECURRING
}
