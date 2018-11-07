package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class DateTimeToLongAttributeConverterTest {

    private static final Long MILLIS = new Long(1460542200000L);
    private static final DateTime DATETIME = DateTime.parse("2016-04-13T10:10:00.000Z");
    
    private DateTimeToLongAttributeConverter converter;
    
    @Before
    public void before() {
        converter = new DateTimeToLongAttributeConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(MILLIS, converter.convertToDatabaseColumn(DATETIME));
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(DATETIME, converter.convertToEntityAttribute(MILLIS));
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
