package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

public class DateTimeZoneAttributeConverterTest {
    
    private static final DateTimeZone ZONE = DateTimeZone.forOffsetHours(-7);
    private static final String OFFSET_STRING = "-07:00";

    private DateTimeZoneAttributeConverter converter;
    
    @Before
    public void before() {
        converter = new DateTimeZoneAttributeConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(OFFSET_STRING, converter.convertToDatabaseColumn(ZONE));
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(ZONE, converter.convertToEntityAttribute(OFFSET_STRING));
    }
    
    @Test
    public void convertToDatabaseColumnNullsafe() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    public void convertToEntityAttributeNullsafe() {
        assertNull(converter.convertToEntityAttribute(null));
    }
}
