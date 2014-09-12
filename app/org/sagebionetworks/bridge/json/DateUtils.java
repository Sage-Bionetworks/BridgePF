package org.sagebionetworks.bridge.json;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public final class DateUtils {

    private static final DateTimeFormatter dateFmt = ISODateTimeFormat.date();
    private static final DateTimeFormatter dateTimeFmt = ISODateTimeFormat.dateTime();

    private static DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }
    
    /**
     * Get number of milliseconds from the epoch as a long at the current system time.
     * 
     * @return milliseconds from epoch.
     */
    public static long getCurrentMillisFromEpoch() {
        return getDateTime().getMillis();
    }

    public static long getMillisFromEpoch(DateTime date) {
        return date.getMillis();
    }
    
    /**
     * Get the current system date in ISO 8601 format (e.g. yyyy-MM-dd).
     * 
     * @return date string
     */
    public static String getCurrentISODate() {
        return dateFmt.withZone(DateTimeZone.UTC).print(getDateTime().getMillis());
    }

    public static String getISODate(DateTime date) {
        return dateFmt.withZone(DateTimeZone.UTC).print(date.getMillis());
    }
    
    /**
     * Get the current system date and time in ISO 8601 format (e.g. yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @return
     */
    public static String getCurrentISODateTime() {
        return dateTimeFmt.withZone(DateTimeZone.UTC).print(getDateTime().getMillis());
    }

    public static String getISODateTime(DateTime date) {
        return dateTimeFmt.withZone(DateTimeZone.UTC).print(date.getMillis());
    }
    
    /**
     * Takes an ISO 8601 format compliant string, and returns only the date part (e.g. yyyy-MM-dd).
     * 
     * @param d
     * @return date string
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static String convertToISODate(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
        } else {
            date = dateTimeFmt.parseDateTime(d);
        }
        return dateFmt.withZone(DateTimeZone.UTC).print(date);
    }

    /**
     * Takes a ISO 8601 compliant string, and returns the full date time (e.g. yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @param d
     * @return date time string
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static String convertToISODateTime(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
            return dateFmt.withZone(DateTimeZone.UTC).print(date);
        } else {
            date = dateTimeFmt.parseDateTime(d);
            return dateTimeFmt.withZone(DateTimeZone.UTC).print(date);
        }
    }

    /**
     * Takes the number of milliseconds from the epoch (long), and returns the full date time (e.g.
     * yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @param millisFromEpoch
     * @return date time string
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static String convertToISODateTime(long millisFromEpoch) {
        return dateTimeFmt.withZone(DateTimeZone.UTC).print(millisFromEpoch);
    }

    /**
     * Takes a ISO 8601 compliant string, and returns that as the number of milliseconds from the epoch.
     * 
     * @param d
     * @return number of milliseconds from epoch
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static long convertToMillisFromEpoch(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
        } else {
            date = dateTimeFmt.parseDateTime(d);
        }
        return date.getMillis();
    }

}
