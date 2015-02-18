package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;

public class DateUtilsTest {
    
    // The test date is: either "2014-08-11T16:01:23.817Z" or 1407772883817 in milliseconds
    private static long MILLIS = 1407772883817L;

    private static String ISO_DATE_TIME = "2014-08-11T16:01:23.817Z";
    private static final long SIX_DAYS_IN_MILLIS = 6*24*60*60*1000;
    
    private DateTime getDateTime() {
        return new DateTime(MILLIS, DateTimeZone.UTC);
    }

    @Test
    public void getDurationFromMillis() {
        String duration = DateUtils.convertToDuration(SIX_DAYS_IN_MILLIS);
        assertEquals("Comes out as 144 hrs", "PT144H", duration);
    }
    
    @Test
    public void getMillisFromDuration() {
        long millis = DateUtils.convertToMillisFromDuration("PT144H");
        assertEquals("Comes out as six days", SIX_DAYS_IN_MILLIS, millis);
    }

    @Test
    public void getCalendarDateString() {
        LocalDate date = new LocalDate(2014, 2, 16);
        assertEquals("2014-02-16", DateUtils.getCalendarDateString(date));
    }

    @Test
    public void parseCalendarDate() {
        LocalDate date = DateUtils.parseCalendarDate("2014-02-16");
        assertEquals(2014, date.getYear());
        assertEquals(2, date.getMonthOfYear());
        assertEquals(16, date.getDayOfMonth());
    }

    @Test
    public void getISODateTime() {
        String dateString = DateUtils.getISODateTime(getDateTime());
        assertEquals("Datetime is correctly formatted", ISO_DATE_TIME, dateString);
    }

    @Test
    public void parseISODateTime() {
        // Arbitrarily 2014-02-17T23:00Z.
        long expectedMillis = new DateTime(2014, 2, 17, 23, 0, DateTimeZone.UTC).getMillis();

        DateTime dateTime = DateUtils.parseISODateTime("2014-02-17T23:00Z");
        assertEquals(expectedMillis, dateTime.getMillis());
    }

    @Test
    public void parseISODateTimeNonUtc() {
        // Arbitrarily 2014-02-17T23:00-0800. We want to use a timezone other than UTC to make sure we can handle
        // non-UTC timezones.
        long expectedMillis = new DateTime(2014, 2, 17, 23, 0, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        DateTime dateTime = DateUtils.parseISODateTime("2014-02-17T23:00-0800");
        assertEquals(expectedMillis, dateTime.getMillis());
    }

    @Test
    public void convertToMillisFromDate() {
        long expectedMillis = new DateTime(2014, 2, 17, 0, 0, DateTimeZone.UTC).getMillis();

        long millis = DateUtils.convertToMillisFromEpoch("2014-02-17");
        assertEquals(expectedMillis, millis);
    }

    @Test
    public void convertToMillisFromDateTime() {
        // Arbitrarily 2014-02-17T23:00Z.
        long expectedMillis = new DateTime(2014, 2, 17, 23, 0, DateTimeZone.UTC).getMillis();

        long millis = DateUtils.convertToMillisFromEpoch("2014-02-17T23:00Z");
        assertEquals(expectedMillis, millis);
    }

    @Test
    public void convertToMillisFromDateTimeNonUtc() {
        // Arbitrarily 2014-02-17T23:00-0800. We want to use a timezone other than UTC to make sure we can handle
        // non-UTC timezones.
        long expectedMillis = new DateTime(2014, 2, 17, 23, 0, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        long millis = DateUtils.convertToMillisFromEpoch("2014-02-17T23:00-0800");
        assertEquals(expectedMillis, millis);
    }
}
