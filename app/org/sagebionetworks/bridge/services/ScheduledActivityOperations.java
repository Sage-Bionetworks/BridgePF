package org.sagebionetworks.bridge.services;

import static java.util.Comparator.comparing;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * If scheduled activity is not in the database, add it to the list of items to be saved as well as to the 
 * results (unless it has expired; in this case it will never be used). Otherwise, add the persisted version 
 * of the activity to the results. Return the results sorted by scheduled time, limited only to the statuses 
 * that are visible to the user.
 */
public class ScheduledActivityOperations {

    private final List<ScheduledActivity> saves = Lists.newArrayList();
    private final List<ScheduledActivity> results = Lists.newArrayList();
    
    public ScheduledActivityOperations(List<ScheduledActivity> scheduledActivities, List<ScheduledActivity> dbActivities) {
        Map<String, ScheduledActivity> dbMap = Maps.uniqueIndex(dbActivities, ScheduledActivity::getGuid);
        
        for (ScheduledActivity activity : scheduledActivities) {
            ScheduledActivity dbActivity = dbMap.get(activity.getGuid());
            if (dbActivity != null) {
                results.add(dbActivity);
            } else {
                // but don't save expired tasks, there is no point
                if (activity.getStatus() != ScheduledActivityStatus.EXPIRED) {
                    saves.add(activity);    
                }
                results.add(activity);
            }
        }
    }
    
    public List<ScheduledActivity> getSaves() {
        return this.saves;
    }
    
    public List<ScheduledActivity> getResults() {
        return this.results.stream()
            .filter(activity -> ScheduledActivityStatus.VISIBLE_STATUSES.contains(activity.getStatus()))
            .sorted(comparing(ScheduledActivity::getScheduledOn))
            .collect(toImmutableList());
    }
    
}
