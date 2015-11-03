package org.sagebionetworks.bridge.models.schedules;

import java.util.EnumSet;
import java.util.Set;

public enum ScheduledActivityStatus {
    /**
     * Scheduled activity has a scheduled start time in the future and has not been started. 
     * It can be shown to the user, but whether it can be started prior to the scheduled 
     * start time is up to the client application.
     */
    SCHEDULED,
    /**
     * Scheduled activity is within the scheduling window but has not been marked as started by the user. 
     */
    AVAILABLE,
    /**
     * The user has started the activity (regardless of scheduling information). 
     */
    STARTED,
    /**
     * The user has finished the activity (regardless of scheduling information).
     */
    FINISHED,
    /**
     * The activity schedule window has passed without the activity being started; at this point 
     * the client probably will not let the user start or do the activity. 
     */
    EXPIRED,
    /**
     * The activity has a finished timestamp but no startedOn timestamp, presumably deleted
     * without being started.
     */
    DELETED;
    
    /**
     * If a scheduled activity has not been started by the user, it can be deleted. However, if it is finished, 
     * or deleted, it shouldn't be visible and we won't delete so that a trivial change to a schedule plan 
     * won't cause the activity to appear again in the interface. 
     */
    public static final Set<ScheduledActivityStatus> DELETABLE_STATUSES = EnumSet.of(ScheduledActivityStatus.SCHEDULED,
            ScheduledActivityStatus.AVAILABLE, ScheduledActivityStatus.EXPIRED);
    
}
