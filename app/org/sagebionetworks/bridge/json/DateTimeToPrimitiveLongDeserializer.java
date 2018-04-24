package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.sagebionetworks.bridge.time.DateUtils;

/**
 * The normal DateTimeJsonDeserializer will treat JSON empty and null values as null Longs. But when 
 * these are sent for fields that are primitive longs, a JSON deserialization exception occurs. 
 * To the JSON producer, this makes no sense (it's strictly because of the internal Java type system). 
 * 
 * This deserializer uses the default primitive long value (0L) which does not throw an exception; the 
 * system should validate that this value is acceptable. In our system, in all places where we set 
 * primitive long timestamps, values from the client are ignored and instead set on the server. 
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
