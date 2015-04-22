package org.sagebionetworks.bridge.models.schedules;

public enum TaskStatus {
    /**
     * Task has a scheduled start time in the future and has not been started. It can be 
     * shown to the user, but whether it can be started prior to the scheduled start time 
     * is up to the client application.
     */
    SCHEDULED,
    /**
     * Task is within the scheduling window but has not been marked as started by the user. 
     */
    AVAILABLE,
    /**
     * The user has started the task (regardless of scheduling information). 
     */
    STARTED,
    /**
     * The user has finished the task (regardless of scheduling information).
     */
    FINISHED,
    /**
     * The task schedule window has passed without the task being started; at this point 
     * the client probably will not let the user start or do the task. 
     */
    EXPIRED;
}
