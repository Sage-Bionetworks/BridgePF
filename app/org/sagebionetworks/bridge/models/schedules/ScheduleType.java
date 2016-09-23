package org.sagebionetworks.bridge.models.schedules;

public enum ScheduleType {
    /** Should be scheduled one time, and once completed, never be scheduled again. */
    ONCE,
    /** Should be scheduled periodically, based on a cron schedule or an interval. */
    RECURRING,
    /** Once triggered by an event, this schedule makes an activity permanently available. 
     * Any time it is finished, an identical scheduled activity is issued. */
    PERSISTENT
}
