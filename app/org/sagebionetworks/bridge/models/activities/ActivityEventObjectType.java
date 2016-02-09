package org.sagebionetworks.bridge.models.activities;

public enum ActivityEventObjectType {
    /**
     * An enrollment event. The eventId will be "enrollment".
     */
    ENROLLMENT,
    /**
     * Event for when a question has been answered. An event record is created for each answer 
     * submitted to the survey response API (NOTE: No application as of 2/9/2016 uses this 
     * API for historical reasons, so we can't schedule based on the answers to survey questions).
     * Event IDs take the format "question:<guid>:answered=<answer_values>".
     */
    QUESTION,
    /**
     * Event for when a survey has been finished. This event is recorded when we have received an 
     * answer (or a declined-to-answer) for every question in a survey through the survey response 
     * API. (NOTE: No application as of 2/9/2016 uses this API for historical reasons, so we can't 
     * schedule based on users finishing a survey). Event IDs take the format 
     * "survey:<guid>:finished".
     */
    SURVEY,
    /**
     * Event for when any activity has been finished. An event is published every time the client 
     * updates a scheduled activity record with a finishedOn timestamp. Clients that use the scheduled 
     * events API do send these updates and we can schedule against the completion of a survey or task. 
     * Event IDs take the format "activity:<guid>:finished" (The guid is the guid of the activity as 
     * saved in a schedule plan).
     */
    ACTIVITY,
    /** 
     * This is a calculated event timestamp based on enrollment. It allows us to create schedules
     * where the participant joins scheduling "mid-stream". For example, in FPHS, activities are 
     * all scheduled from Saturday to Saturday using a cron expression, and a user, when they join, 
     * get the tasks assigned the previous Saturday. This event can be used for periodic tasks 
     * that happen weekly or biweekly.
     * @see org.sagebionetworks.bridge.dao.ActivityEventDao#getActivityEventMap 
     */
    TWO_WEEKS_BEFORE_ENROLLMENT,
    /** 
     * This is a calculated event timestamp based on enrollment. It allows us to create schedules
     * where the participant joins scheduling "mid-stream". For example, in FPHS, activities are 
     * all scheduled from Saturday to Saturday using a cron expression, and a user, when they join, 
     * get the tasks assigned the previous Saturday. This event can be used for periodic tasks 
     * that happen monthly or bimonthly.
     * @see org.sagebionetworks.bridge.dao.ActivityEventDao#getActivityEventMap 
     */
    TWO_MONTHS_BEFORE_ENROLLMENT;
}
