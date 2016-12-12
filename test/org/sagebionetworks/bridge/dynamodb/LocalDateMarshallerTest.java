package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.junit.Test;

public class LocalDateMarshallerTest {
    private static final LocalDateMarshaller MARSHALLER = new LocalDateMarshaller();

    @Test
    public void testMarshall() {
        assertEquals("2014-12-25", MARSHALLER.convert(new LocalDate(2014, 12, 25)));
    }

    @Test
    public void testUnmarshall() {
        LocalDate calendarDate = MARSHALLER.unconvert("2014-10-31");
        assertEquals(2014, calendarDate.getYear());
        assertEquals(10, calendarDate.getMonthOfYear());
        assertEquals(31, calendarDate.getDayOfMonth());
    }
}
