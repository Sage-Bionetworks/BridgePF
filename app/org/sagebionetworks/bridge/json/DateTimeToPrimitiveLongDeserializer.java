package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * The normal DateTimeJsonDeserializer will treat JSON empty and null values as null Longs, 
 * which is fine. But when these are sent for fields that are primitive longs, a JSON deserialization 
 * error occurs. To the JSON producer, this makes no sense (it's strictly because of the internal Java 
 * type system). 
 * 
 * This deserializer uses the default primitive long value (0L); the system should validate that this 
 * value is acceptable. In our system, in all places where we set primitive long timestamps, 
 * values from the client are ignored and these are set on the server. With this deserializer, they 
 * are properly ignored during deserialization, instead of throwing an error.
 */
public class DateTimeToPrimitiveLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String date = jp.getText();
        return DateUtils.convertToMillisFromEpoch(date);
    }
    
    @Override
    public Long getNullValue() {
        return 0L;
    }
    
    @Override
    public Long getEmptyValue() {
        return 0L;
    }
}
