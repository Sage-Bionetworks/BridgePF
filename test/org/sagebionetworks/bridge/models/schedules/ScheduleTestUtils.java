package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class ScheduleTestUtils {

    /**
     * Create a DateTime from a string like "2015-05-01 20:00" in a given time zone
     * @param string
     * @return
     */
    public static DateTime asDT(String string, DateTimeZone zone) {
        String offset = (DateTimeZone.UTC == zone) ? "Z" : zone.toString();
        return DateTime.parse(string.replace(" ", "T") + ":00"+offset);
    }

    /**
     * Create a DateTime from a string like "2015-05-01 20:00".
     * @param string
     * @return
     */
    public static DateTime asDT(String string) {
        return asDT(string, DateTimeZone.UTC);
    }
    
    /**
     * Create a DateTime from a string like "2015-05-01 20:00".
     * @param string
     * @return
     */
    public static long asLong(String string) {
        return asDT(string).getMillis();
    }
    
    /**
     * Assert that a list of activities has the list of startsOn dates, in the 
     * order specified.
     * @param activities
     * @param output
     */
    public static void assertDates(List<ScheduledActivity> activities, DateTimeZone zone, String... output) {
        assertEquals(output.length, activities.size());
        for (int i=0; i < activities.size(); i++) {
            DateTime datetime = asDT(output[i], zone);
            assertEquals(datetime, activities.get(i).getScheduledOn());
        }
    }
    
    public static void assertDates(List<ScheduledActivity> activities, String... output) {
        assertDates(activities, DateTimeZone.UTC, output);
    }
}
