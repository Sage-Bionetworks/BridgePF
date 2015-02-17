package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.joda.time.DateTime;

/**
 * Custom serializer for Joda DateTime, because the one in jackson-datatype-joda serializes to a long. This one
 * serializes to a string in yyyy-MM-dd'T'HH:mm:ss.SSSZZ format.
 */
public class DateTimeToStringSerializer extends JsonSerializer<DateTime> {
    /** {@inheritDoc} */
    @Override
    public void serialize(DateTime dateTime, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeString(DateUtils.getISODateTime(dateTime));
    }
}
