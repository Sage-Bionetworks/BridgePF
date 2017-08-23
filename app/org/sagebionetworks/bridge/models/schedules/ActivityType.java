package org.sagebionetworks.bridge.models.schedules;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

public enum ActivityType {
    COMPOUND("compoundactivities"),
    SURVEY("surveys"),
    TASK("tasks");
    
    private final String plural;
    
    private ActivityType(String plural ){
        this.plural = plural;
    }
    
    public static final Map<String,ActivityType> PLURALS = new ImmutableMap.Builder<String,ActivityType>()
            .put(COMPOUND.plural, COMPOUND)
            .put(SURVEY.plural, SURVEY)
            .put(TASK.plural, TASK).build();
}
