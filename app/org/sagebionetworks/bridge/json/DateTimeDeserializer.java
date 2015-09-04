package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.joda.time.DateTime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Convert DateTime using the time zone submitted as part of the datetime 
 * string. Joda's Jackson module does not do this. Not clear at this point 
 * how much of the Joda Module we *are* using.
 */
public class DateTimeDeserializer  extends JsonDeserializer<DateTime> {

    @Override
    public DateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String date = jp.getText();
        return DateTime.parse(date);
    }
    
    @Override
    public DateTime getNullValue() {
        return null;
    }
    
    @Override
    public DateTime getEmptyValue() {
        return null;
    }
    
}

