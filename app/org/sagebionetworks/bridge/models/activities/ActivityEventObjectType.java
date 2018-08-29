package org.sagebionetworks.bridge.models.activities;

import java.util.EnumSet;

public enum ActivityEventObjectType {
    /**
     * Event for the first time the user successfully requests scheduled activities from the 
     * server. This event is not recorded until the activities can be successfully returned to 
     * the user, so if consent is required, it will have to be provided first. This event does 
     * not update after creation.
     */
    ACTIVITIES_RETRIEVED,
    /**
     * An enrollment event. The eventId will be "enrollment". This event does not update after 
     * creation.
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
     * A custom event defined at the study level.
     */
    CUSTOM;
    
    public static final EnumSet<ActivityEventObjectType> UNARY_EVENTS = EnumSet.of(ENROLLMENT, ACTIVITIES_RETRIEVED);
}
