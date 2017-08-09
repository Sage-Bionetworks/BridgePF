package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.joda.time.DateTime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Convert DateTime preserving the time zone. Joda's Jackson module does not do this.
 * Not clear at this point how much of the Joda Module we *are* using.
 */
public class DateTimeSerializer extends JsonSerializer<DateTime> {

    @Override
    public void serialize(DateTime datetime, JsonGenerator jgen, SerializerProvider sp) throws IOException,
            JsonProcessingException {
        jgen.writeString(datetime.toString());
    }
    
    @Override
    public boolean isEmpty(DateTime datetime) {
        return (datetime == null);
    }
    
}