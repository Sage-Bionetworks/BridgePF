package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ScheduledActivityOperationsTest {

    @Test
    public void newActivitiesIncludedInSaveAndResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "BBB");
        List<ScheduledActivity> db = createActivities("BBB");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("AAA","BBB"), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet("AAA"), toGuids(operations.getSaves()));
        assertEquals(Sets.newHashSet(), toGuids(operations.getDeletes()));
    }
    
    @Test
    public void persistedAndScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("CCC");
        List<ScheduledActivity> db = createActivities("CCC");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("CCC"), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet(), toGuids(operations.getSaves()));
        assertEquals(Sets.newHashSet(), toGuids(operations.getDeletes()));
    }
    
    @Test
    public void persistedNotScheduledIncludedInDeletes() {
        List<ScheduledActivity> scheduled = Lists.newArrayList();
        List<ScheduledActivity> db = createActivities("CCC");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet(), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet(), toGuids(operations.getSaves()));
        assertEquals(Sets.newHashSet("CCC"), toGuids(operations.getDeletes()));
    }
    
    @Test
    public void startedNotScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA");
        List<ScheduledActivity> db = createActivities("CCC");
        db.get(0).setStartedOn(new Long(1234L)); // started, not scheduled
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("AAA","CCC"), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet("AAA"), toGuids(operations.getSaves()));
        assertEquals(Sets.newHashSet(), toGuids(operations.getDeletes()));
    }
    
    private List<ScheduledActivity> createActivities(String... guids) {
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setGuid(guid);
            list.add(activity);
        }
        return list;
    }
    
    private Set<String> toGuids(List<ScheduledActivity> activities) {
        return activities.stream().map(ScheduledActivity::getGuid).collect(Collectors.toSet());
    }
    
}
