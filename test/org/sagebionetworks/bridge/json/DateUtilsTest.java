package org.sagebionetworks.bridge.json;

import static org.junit.Assert.*;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class DateUtilsTest {
    
    // The test date is: either "2014-08-11T16:01:23.817Z" or 1407772883817 in milliseconds
    private static long MILLIS = 1407772883817L;
    private static long MILLIS_TO_MIDNIGHT = 1407715200000L;

    private static String ISO_DATE_TIME = "2014-08-11T16:01:23.817Z";
    private static String ISO_DATE = "2014-08-11";
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
    public void getMillisFromEpoch() {
        long millis = DateUtils.getMillisFromEpoch(getDateTime());
        assertEquals("Milliseconds are correct", MILLIS, millis);
    }
    @Test
    public void getISODate() {
        String dateString = DateUtils.getISODate(getDateTime());
        assertEquals("Date is correctly formatted", ISO_DATE, dateString);
    }
    @Test
    public void getISODateTime() {
        String dateString = DateUtils.getISODateTime(getDateTime());
        assertEquals("Datetime is correctly formatted", ISO_DATE_TIME, dateString);
    }
    @Test
    public void convertToISODate() {
        // No problem converting the more specific datetime to a date only
        
        String dateString = DateUtils.convertToISODate(ISO_DATE);
        assertEquals("Same date string [1]", ISO_DATE, dateString);
        
        dateString = DateUtils.convertToISODate(ISO_DATE_TIME);
        assertEquals("Same date string [2]", ISO_DATE, dateString);
    }
    @Test
    public void convertToISODateTime() {
        // But we cannot convert a date only to a date time, without falsely 
        // declaring a time, usually of midnight. So we don't, we pass back a 
        // date only.
        
        String dateString = DateUtils.convertToISODateTime(ISO_DATE);
        assertEquals("Same date time string [1]", ISO_DATE, dateString);
        
        dateString = DateUtils.convertToISODateTime(ISO_DATE_TIME);
        assertEquals("Same date time string [2]", ISO_DATE_TIME, dateString);
    }
    @Test
    public void convertToMillisFromEpoch() {
        long millis = DateUtils.convertToMillisFromEpoch(ISO_DATE_TIME);
        assertEquals("Same millis [1]", MILLIS, millis);
        
        // We cannot avoid ambiguity here though, as long can't represent this.
        // So this is milliseconds to midnight UTC of the given date.
        millis = DateUtils.convertToMillisFromEpoch(ISO_DATE);
        assertEquals("Same millis [2]", MILLIS_TO_MIDNIGHT, millis);
    }
}
