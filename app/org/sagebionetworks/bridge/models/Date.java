package org.sagebionetworks.bridge.models;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Date {
    private final long millisFromEpoch;
    
    private final DateTimeFormatter millisFmt = DateTimeFormat.longTime();
    private final DateTimeFormatter dateFmt = DateTimeFormat.fullDate();
    private final DateTimeFormatter dateTimeFmt = DateTimeFormat.fullDateTime();
    
    public Date(long millisFromEpoch) {
        this.millisFromEpoch = millisFromEpoch;
    }
    
    public long getMillisFromEpoch() {
        return millisFromEpoch.getMillis();
    }
    
    public String getISODate() {
        return dateFmt.print(millisFromEpoch);
    }
    
}
