package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDateTime;
import org.junit.Test;

public class LocalDateTimeMarshallerTest {
    private static final LocalDateTimeMarshaller MARSHALLER = new LocalDateTimeMarshaller();

    @Test
    public void testMarshall() {
        assertEquals("2014-12-25T10:12:37.022", MARSHALLER.convert(new LocalDateTime(2014, 12, 25, 10, 12, 37, 22)));
    }

    @Test
    public void testUnmarshall() {
        LocalDateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12:37.022");
        assertEquals(2014, dateTime.getYear());
        assertEquals(10, dateTime.getMonthOfYear());
        assertEquals(31, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHourOfDay());
        assertEquals(12, dateTime.getMinuteOfHour());
        assertEquals(37, dateTime.getSecondOfMinute());
        assertEquals(22, dateTime.getMillisOfSecond());
    }
    
    @Test
    public void testUnmarshallOfPartialLocalDateTime() {
        LocalDateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12");
        assertEquals(2014, dateTime.getYear());
        assertEquals(10, dateTime.getMonthOfYear());
        assertEquals(31, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHourOfDay());
        assertEquals(12, dateTime.getMinuteOfHour());
        assertEquals(0, dateTime.getSecondOfMinute());
        assertEquals(0, dateTime.getMillisOfSecond());
    }

}
