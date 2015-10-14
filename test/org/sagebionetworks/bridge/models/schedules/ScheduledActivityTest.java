package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;

import com.google.common.collect.Lists;

public class ScheduledActivityTest {

    @Test
    public void testComparator() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setTimeZone(DateTimeZone.UTC);
        activity1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        activity1.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // Definitely later
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity2.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // The same as 2 in all respects but activity label comes earlier in alphabet
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        assertEquals(activity1, activities.get(0));
        assertEquals(activity3, activities.get(1));
        assertEquals(activity2, activities.get(2));
    }
    
    @Test
    public void handlesNullFieldsReasonably() {
        // No time zone
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setScheduledOn(DateTime.parse("2010-10-10T01:01:01.000Z"));
        activity1.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // scheduledOn
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setActivity(TestConstants.TEST_3_ACTIVITY);
        
        // This one is okay
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setScheduledOn(DateTime.parse("2011-10-10T01:01:01.000Z"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        // Activity 3 comes first because it's complete, the others follow. This is arbitrary...
        // in reality they are broken activities, but the comparator will not fail.
        assertEquals(activity3, activities.get(0));
        assertEquals(activity1, activities.get(1));
        assertEquals(activity2, activities.get(2));
    }
    
}
