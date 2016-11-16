package org.sagebionetworks.bridge.json;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;

public final class DateUtils {
    private static final int CALENDAR_DATE_STRLEN = "yyyy-MM-dd".length();
    private static final DateTimeFormatter dateFmt = ISODateTimeFormat.date();
    private static final DateTimeFormatter dateTimeFmt = ISODateTimeFormat.dateTime();
    private static final PeriodFormatter periodFmt = ISOPeriodFormat.standard();
    private static final Pattern OFFSET_PATTERN_HOURS_ONLY = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("^[+-]\\d{1,2}:\\d{2}$");
    private static final String TIMEZONE_OFFSET_FORMAT_ERROR = "Cannot not parse timezone offset '%s' (use format ±HH:MM)";

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
        if (dateTimeStr.length() <= CALENDAR_DATE_STRLEN) {
            // String too short. This can't possibly be an ISO timestamp.
            throw new IllegalArgumentException("Malformatted timestamp: " + dateTimeStr);
        }

        // We use dateTimeParser() instead of dateTimeFmt because we want to be able to handle non-UTC timezones.
        // withOffsetParsed() is needed, otherwise Joda converts everything to the local timezone.
        return ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(dateTimeStr);
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
        int strlen = d.length();
        if (strlen == CALENDAR_DATE_STRLEN) {
            LocalDate localDate = parseCalendarDate(d);
            date = localDate.toDateTimeAtStartOfDay(DateTimeZone.UTC);
        } else if (strlen > CALENDAR_DATE_STRLEN) {
            date = parseISODateTime(d);
        } else {
            // String too short. This can't possibly be an ISO timestamp.
            throw new IllegalArgumentException("Malformatted timestamp: " + d);
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

    /**
     * Convert at 8601 time zone offset string (e.g. "-07:00") to a DateTimeZone 
     * object. 
     * @param offsetString
     * @return
     */
    public static DateTimeZone parseZoneFromOffsetString(String offsetString) {
        DateTimeZone zone = null;
        if (StringUtils.isNotBlank(offsetString)) {
            try {
                if (OFFSET_PATTERN_HOURS_ONLY.matcher(offsetString).matches()) {
                    int hours = Integer.parseInt(offsetString);
                    zone = DateTimeZone.forOffsetHours(hours);
                } else if (OFFSET_PATTERN.matcher(offsetString).matches()) {
                    String[] zoneParts = offsetString.split(":");
                    int hours = Integer.parseInt(zoneParts[0]);
                    int minutes = Integer.parseInt(zoneParts[1]);
                    zone = DateTimeZone.forOffsetHoursMinutes(hours, minutes);
                }
            } catch(RuntimeException throwable) {
                // Do nothing, throw exception one time below whether pattern was not matched 
                // or there was an error in parsing, e.g. an offset of -1000 or something.
            }
            if (zone == null) {
                throw new IllegalArgumentException(String.format(TIMEZONE_OFFSET_FORMAT_ERROR, offsetString));
            }
        }
        return zone;
    }
    
    /**
     * If no value is provided, returns the default datetime value. Otherwise, returns the datetime 
     * of the string if it can be parsed or an exception if it's not a valid ISO 8601 timestamp.
     * @param value
     * @param defaultValue
     * @return
     */
    public static DateTime getDateTimeOrDefault(String value, DateTime defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return DateUtils.parseISODateTime(value);
        } catch(Exception e) {
            throw new BadRequestException(value + " is not an ISO timestamp");
        }
    }
    
    /**
     * Convert DateTime instance to midnight of a given day, using UTC time zone.
     * @param dateTime
     * @return dateTime at midnight in UTC
     */
    public static DateTime dateTimeToUTCMidnight(DateTime dateTime) {
        return new DateTime(dateTime, DateTimeZone.UTC).withTime(LocalTime.MIDNIGHT);
    }
}
