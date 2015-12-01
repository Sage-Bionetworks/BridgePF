package org.sagebionetworks.bridge.services;

import java.util.Set;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.google.common.collect.Sets;

final class ScheduledActivityOperations {

    private final Set<ScheduledActivity> saves;
    private final Set<ScheduledActivity> deletes;
    private final Set<ScheduledActivity> results;
    
    ScheduledActivityOperations() {
        saves = Sets.newHashSet();
        deletes = Sets.newHashSet();
        results = Sets.newHashSet();
    }
    
    void save(ScheduledActivity activity) {
        saves.add(activity);
        results.add(activity);
    }
    
    void delete(ScheduledActivity activity) {
        deletes.add(activity);
    }
    
    void result(ScheduledActivity activity) {
        results.add(activity);
    }
    
    Set<ScheduledActivity> getSavables() {
        return saves;
    }
    
    Set<ScheduledActivity> getDeletables() {
        return deletes;
    }
    
    Set<ScheduledActivity> getResults() {
        return results;
    }

}
