package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.sagebionetworks.bridge.time.DateUtils;

public final class DateTimeToLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String date = jp.getText();
        return DateUtils.convertToMillisFromEpoch(date);
    }
    
    @Override
    public Long getNullValue() {
        return null;
    }
    
    @Override
    public Long getEmptyValue() {
        return null;
    }
    
}
