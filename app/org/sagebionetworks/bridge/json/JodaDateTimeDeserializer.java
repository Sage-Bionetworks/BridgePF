package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Custom Joda DateTime deserializer, because the one in jackson-datatype-joda doesn't work. This one deserializes from
 * a string in yyyy-MM-dd'T'HH:mm:ss.SSSZZ format.
 */
public class JodaDateTimeDeserializer extends JsonDeserializer<DateTime> {
    /** {@inheritDoc} */
    @Override
    public DateTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String dateTimeStr = jp.getText();
        return ISODateTimeFormat.dateTimeParser().parseDateTime(dateTimeStr);
    }
}
