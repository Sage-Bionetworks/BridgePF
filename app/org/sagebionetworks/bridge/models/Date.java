package org.sagebionetworks.bridge.models;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Date {
    
    private final long millisFromEpoch;
    private final String isoDate;
    private final String isoDateTime;
    
    private static final DateTimeFormatter dateFmt = DateTimeFormat.forPattern("yyyy-mm-dd");
    private static final DateTimeFormatter dateTimeFmt = DateTimeFormat.forPattern("yyyy-MM-ddTHH:mm:ss.SSSZ");
}
