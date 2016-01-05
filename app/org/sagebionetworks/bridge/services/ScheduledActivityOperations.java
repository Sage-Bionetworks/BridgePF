package org.sagebionetworks.bridge.services;

import static java.util.stream.Collectors.toList;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.google.common.collect.Lists;

/**
 * On each request we compare the activities scheduled for a user against what is persisted 
 * in the database, calculating new activities to save and invalid activities to delete.
 */
public class ScheduledActivityOperations {

    private final List<ScheduledActivity> saves = Lists.newArrayList();
    private final List<ScheduledActivity> deletes = Lists.newArrayList();
    private final List<ScheduledActivity> results = Lists.newArrayList();
    
    private final List<String> scheduledGuids;
    private final List<String> dbGuids;

    public ScheduledActivityOperations(List<ScheduledActivity> scheduledActivities, List<ScheduledActivity> dbActivities) {
        this.scheduledGuids = scheduledActivities.stream().map(ScheduledActivity::getGuid).collect(toList());
        this.dbGuids = dbActivities.stream().map(ScheduledActivity::getGuid).collect(toList());
        
        for (ScheduledActivity activity : dbActivities) {
            // Once started, persisted activity is included in results
            // activity is scheduled and persisted, include the persisted version in results
            if (scheduledGuids.contains(activity.getGuid()) || (activity.getStatus() == ScheduledActivityStatus.STARTED)) {
                results.add(activity);
            } else {
                // but if it's in database and not scheduled, it's obsolete, delete it
                deletes.add(activity);
            }
        }
        // Are scheduled activities new? Save them and include in results
        for (ScheduledActivity activity : scheduledActivities) {
            if (!dbGuids.contains(activity.getGuid())) {
                saves.add(activity);
                results.add(activity);
            }
        }
    }
    
    public List<ScheduledActivity> getSaves() {
        return this.saves;
    }
    public List<ScheduledActivity> getDeletes() {
        return this.deletes;
    }
    public List<ScheduledActivity> getResults() {
        return this.results;
    }
    
}
