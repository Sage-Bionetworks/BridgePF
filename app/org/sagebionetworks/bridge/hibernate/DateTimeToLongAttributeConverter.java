package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

@Converter
public class DateTimeToLongAttributeConverter implements AttributeConverter<DateTime, Long> {

    @Override
    public Long convertToDatabaseColumn(DateTime dateTime) {
        return (dateTime == null) ? null : dateTime.getMillis();
    }

    @Override
    public DateTime convertToEntityAttribute(Long longValue) {
        // We do lose the time zone when storing this value as a long, we reflect this by using UTC.
        return (longValue == null) ? null : new DateTime(longValue).withZone(DateTimeZone.UTC);
    }

}
