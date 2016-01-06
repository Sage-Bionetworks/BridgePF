package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ScheduledActivityOperationsTest {

    private static final HashSet<Object> EMPTY_SET = Sets.newHashSet();

    @Test
    public void newActivitiesIncludedInSaveAndResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "BBB");
        List<ScheduledActivity> db = createActivities("BBB");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("AAA","BBB"), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet("AAA"), toGuids(operations.getSaves()));
    }
    
    @Test
    public void persistedAndScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("CCC");
        List<ScheduledActivity> db = createActivities("CCC");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("CCC"), toGuids(operations.getResults()));
        assertEquals(EMPTY_SET, toGuids(operations.getSaves()));
    }
    
    @Test
    public void persistedNotScheduledIncludedInDeletes() {
        List<ScheduledActivity> scheduled = Lists.newArrayList();
        List<ScheduledActivity> db = createActivities("CCC");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(EMPTY_SET, toGuids(operations.getResults()));
        assertEquals(EMPTY_SET, toGuids(operations.getSaves()));
    }
    
    @Test
    public void startedNotScheduledIncludedInResults() {
        List<ScheduledActivity> scheduled = createActivities("AAA", "CCC");
        List<ScheduledActivity> db = createActivities("CCC");
        db.get(0).setStartedOn(new Long(1234L)); // started, not scheduled
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        
        assertEquals(Sets.newHashSet("AAA","CCC"), toGuids(operations.getResults()));
        assertEquals(Sets.newHashSet("AAA"), toGuids(operations.getSaves()));
    }
    
    @Test
    public void expiredTasksExcludedFromCalculations() {
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
        
        // create activities in the past that are now expired.
        List<ScheduledActivity> scheduled = createOldActivities(PST, "AAA","BBB");
        List<ScheduledActivity> db = createOldActivities(PST, "AAA","CCC");
        
        ScheduledActivityOperations operations = new ScheduledActivityOperations(scheduled, db);
        assertEquals(EMPTY_SET, toGuids(operations.getResults()));
        assertEquals(EMPTY_SET, toGuids(operations.getSaves()));
    }
    
    private List<ScheduledActivity> createActivities(String... guids) {
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setGuid(guid);
            activity.setTimeZone(DateTimeZone.UTC);
            activity.setScheduledOn(DateTime.now());
            list.add(activity);
        }
        return list;
    }
    
    private List<ScheduledActivity> createOldActivities(DateTimeZone timeZone, String... guids) {
        DateTime startedOn = DateTime.now().minusMonths(6);
        DateTime expiresOn = DateTime.now().minusMonths(5);
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setTimeZone(timeZone);
            activity.setGuid(guid);
            activity.setScheduledOn(startedOn);
            activity.setExpiresOn(expiresOn);
            list.add(activity);
        }
        return list;
    }
    
    private Set<String> toGuids(List<ScheduledActivity> activities) {
        return activities.stream().map(ScheduledActivity::getGuid).collect(Collectors.toSet());
    }
    
}
