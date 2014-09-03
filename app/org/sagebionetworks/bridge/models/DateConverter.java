package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public final class DateConverter {

    private static final DateTimeFormatter dateFmt = ISODateTimeFormat.date();
    private static final DateTimeFormatter dateTimeFmt = ISODateTimeFormat.dateTime();

    /**
     * Get number of milliseconds from the epoch as a long at the current system time.
     * 
     * @return milliseconds from epoch.
     */
    public static long getCurrentMillisFromEpoch() {
        return DateTime.now(DateTimeZone.UTC).getMillis();
    }

    /**
     * Get the current system date in ISO 8601 format (e.g. yyyy-MM-dd).
     * 
     * @return date string
     */
    public static String getCurrentISODate() {
        return dateFmt.print(DateTime.now(DateTimeZone.UTC).getMillis());
    }

    /**
     * Get the current system date and time in ISO 8601 format (e.g. yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @return
     */
    public static String getCurrentISODateTime() {
        return dateTimeFmt.print(DateTime.now(DateTimeZone.UTC).getMillis());
    }

    /**
     * Takes an ISO 8601 format compliant string, and returns only the date part (e.g. yyyy-MM-dd).
     * 
     * @param d
     * @return date string
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static String convertISODate(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
        } else {
            date = dateTimeFmt.parseDateTime(d);
        }
        return dateFmt.print(date);
    }

    /**
     * Takes a ISO 8601 compliant string, and returns the full date time (e.g. yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @param d
     * @return date time string
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static String convertISODateTime(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
        } else {
            date = dateTimeFmt.parseDateTime(d);
        }
        return dateTimeFmt.print(date);
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
    public static String convertISODateTime(long millisFromEpoch) {
        return dateTimeFmt.print(millisFromEpoch);
    }

    /**
     * Takes a ISO 8601 compliant string, and returns that as the number of milliseconds from the epoch.
     * 
     * @param d
     * @return number of milliseconds from epoch
     * @throws exception
     *             if parameter d is in an incorrect format.
     */
    public static long convertMillisFromEpoch(String d) {
        DateTime date = null;
        if (d.length() == "yyyy-MM-dd".length()) {
            date = dateFmt.parseDateTime(d);
        } else {
            date = dateTimeFmt.parseDateTime(d);
        }
        return date.getMillis();
    }

}
