package org.sagebionetworks.bridge.json;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;

import org.sagebionetworks.bridge.BridgeConstants;

public final class DateUtils {

    private static final DateTimeFormatter dateFmt = ISODateTimeFormat.date();
    private static final DateTimeFormatter dateTimeFmt = ISODateTimeFormat.dateTime();
    private static final PeriodFormatter periodFmt = ISOPeriodFormat.standard();

    /** Returns current time as a Joda DateTime object. */
    public static DateTime getCurrentDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }

    /** Returns current calendar date as a Joda LocalDate object, using UTC as the reference time zone. */
    public static LocalDate getCurrentCalendarDateInUtc() {
        return LocalDate.now(DateTimeZone.UTC);
    }

    /**
     * Returns current calendar date as a Joda LocalDate object, using Pacific local time as the reference time zone.
     */
    public static LocalDate getCurrentCalendarDateInLocalTime() {
        return LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE);
    }
    
    /**
     * Get number of milliseconds from the epoch as a long at the current system time.
     * 
     * @return milliseconds from epoch.
     */
    public static long getCurrentMillisFromEpoch() {
        return getCurrentDateTime().getMillis();
    }

    /** Returns the current calendar date as a string in YYYY-MM-DD format, using UTC as the reference time zone. */
    public static String getCurrentCalendarDateStringInUtc() {
        return getCalendarDateString(getCurrentCalendarDateInUtc());
    }

    /**
     * Returns the current calendar date as a string in YYYY-MM-DD format, using Pacific local time as the reference
     * time zone.
     */
    public static String getCurrentCalendarDateStringInLocalTime() {
        return getCalendarDateString(getCurrentCalendarDateInLocalTime());
    }

    /** Converts the Joda LocalDate object into a string in YYYY-MM-DD format. */
    public static String getCalendarDateString(LocalDate date) {
        return dateFmt.print(date);
    }

    /** Parses a calendar date in YYYY-MM-DD format into a Joda LocalDate. */
    public static LocalDate parseCalendarDate(String dateStr) {
        return dateFmt.parseLocalDate(dateStr);
    }

    /**
     * Get the current system date and time in ISO 8601 format (e.g. yyyy-MM-ddTHH:mm:ss.SSSZ).
     * 
     * @return
     */
    public static String getCurrentISODateTime() {
        return getISODateTime(getCurrentDateTime());
    }

    public static String getISODateTime(DateTime date) {
        return dateTimeFmt.withZone(DateTimeZone.UTC).print(date.getMillis());
    }

    /** Parses a string in ISO 8601 date format into a Joda DateTime object. */
    public static DateTime parseISODateTime(String dateTimeStr) {
        // We use dateTimeParser() instead of dateTimeFmt because we want to be able to handle non-UTC timezones.
        return ISODateTimeFormat.dateTimeParser().parseDateTime(dateTimeStr);
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
     * <p>
     * Takes a ISO 8601 compliant string, and returns that as the number of milliseconds from the epoch.
     * </p>
     * <p>
     * For backwards compatibility, this function accepts calendar dates in YYYY-MM-DD format. Calendar dates cannot be
     * meaningfully converted into time instants. However, for backwards compatibility, this function will assume
     * start of day (usually midnight) at UTC.
     * </p>
     */
    public static long convertToMillisFromEpoch(String d) {
        DateTime date;
        if (d.length() == "yyyy-MM-dd".length()) {
            LocalDate localDate = parseCalendarDate(d);
            date = localDate.toDateTimeAtStartOfDay(DateTimeZone.UTC);
        } else {
            date = parseISODateTime(d);
        }
        return date.getMillis();
    }

    /**
     * Convert a duration in milliseconds to an ISO 8601 Duration value. This is 
     * not necessarily 100% accurate when the durations are very large (e.g. 
     * hours in a day may not be 24 due to daylight savings time, days in a year 
     * may not be 365 due to leap years, etc.) but they will be accurate enough
     * for our purposes.
     * @param millis
     * @return an ISO 8601 Duration value
     */
    public static String convertToDuration(long millis) {
        Period period = new Period(millis);
        return periodFmt.print(period);
    }
    
    /**
     * Convert an 8601 Duration string to a duration in milliseconds.
     * @param duration
     * @return the duration in milliseconds
     */
    public static Long convertToMillisFromDuration(String duration) {
        if (StringUtils.isNotBlank(duration)) {
            Period period = periodFmt.parsePeriod(duration);
            if (period != null) {
                return period.toStandardDuration().getMillis();
            }
        }
        return null;
    }

}
