package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.time.DateUtils;

@Converter
public class DateTimeZoneAttributeConverter implements AttributeConverter <DateTimeZone,String> {

    @Override
    public String convertToDatabaseColumn(DateTimeZone zone) {
        return DateUtils.timeZoneToOffsetString(zone);
    }

    @Override
    public DateTimeZone convertToEntityAttribute(String string) {
        return DateUtils.parseZoneFromOffsetString(string);
    }

}
