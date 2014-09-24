package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class PeriodJsonDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String date = jp.getText();
        return DateUtils.convertToMillisFromDuration(date);
    }
    
    @Override
    public Long getNullValue() {
        return null;
    }
}
