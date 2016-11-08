package org.sagebionetworks.bridge;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class DateTimeTest {

    @Test
    public void test() {
        DateTime now = DateTime.now();
        System.out.println("now: "+ now);
        
        DateTime second = now.withZone(DateTimeZone.UTC);
        System.out.println("second: " + second);
        
        DateTime third = second.minusDays(2);
        System.out.println("third: " + third);
    }
    
}
