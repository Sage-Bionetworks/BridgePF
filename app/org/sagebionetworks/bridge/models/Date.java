package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class Date {
    private final long millisFromEpoch;

    private static final DateTimeFormatter dateFmt = ISODateTimeFormat.date();
    private static final DateTimeFormatter dateTimeFmt = ISODateTimeFormat.dateTime();

    public Date(long millisFromEpoch) {
        this.millisFromEpoch = millisFromEpoch;
    }
    
    public Date(String d) {
        if (d.length() == "yyyy-MM-dd".length()) {
            this.millisFromEpoch = constructFromDate(d).millisFromEpoch;
        } else {
            this.millisFromEpoch = constructFromDateTime(d).millisFromEpoch;
        }
    }

    public static Date constructFromDate(String d) {
        DateTime date = dateFmt.parseDateTime(d);
        return new Date(date.getMillis());
    }

    public static Date constructFromDateTime(String d) {
        DateTime date = dateTimeFmt.parseDateTime(d);
        return new Date(date.getMillis());
    }

    public static long getCurrentMillisFromEpoch() {
        return DateTime.now(DateTimeZone.UTC).getMillis();
    }

    public static String getCurrentISODate() {
        return dateFmt.print(DateTime.now(DateTimeZone.UTC).getMillis());
    }

    public static String getCurrentISODateTime() {
        return dateTimeFmt.print(DateTime.now(DateTimeZone.UTC).getMillis());
    }

    public long getMillisFromEpoch() {
        return millisFromEpoch;
    }

    public String getISODate() {
        return dateFmt.print(millisFromEpoch);
    }

    public String getISODateTime() {
        return dateTimeFmt.print(millisFromEpoch);
    }

}
