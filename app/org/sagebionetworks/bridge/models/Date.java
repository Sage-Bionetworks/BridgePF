package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Date {
    private final long millisFromEpoch;
    
    private final DateTimeFormatter dateFmt = DateTimeFormat.fullDate();
    private final DateTimeFormatter dateTimeFmt = DateTimeFormat.fullDateTime();
    
    public Date(long millisFromEpoch) {
        this.millisFromEpoch = millisFromEpoch;
    }
    
    public Date(String dateTime) {
        DateTime dt = dateTimeFmt.parseDateTime(dateTime);
        millisFromEpoch = dt.getMillis();
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
