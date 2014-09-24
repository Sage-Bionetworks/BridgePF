package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class PeriodJsonSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long millis, JsonGenerator gen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
        if (millis != null && millis != 0L) {
            gen.writeString(DateUtils.convertToDuration(millis));
        }
    }

}
