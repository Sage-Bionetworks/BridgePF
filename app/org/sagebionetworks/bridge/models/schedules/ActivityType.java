package org.sagebionetworks.bridge.models.schedules;

public enum ActivityType {
    COMPOUND,
    SURVEY,
    TASK;
    
    public static ActivityType fromPlural(String plural) {
        if ("tasks".equals(plural)) {
            return ActivityType.TASK;
        } else if ("surveys".equals(plural)) {
            return ActivityType.SURVEY;
        } else if ("compoundactivities".equals(plural)) {
            return ActivityType.COMPOUND;
        }
        return null;
    }
}
