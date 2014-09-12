package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public final class DateTimeJsonSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long millisFromEpoch, JsonGenerator jgen, SerializerProvider sp) throws IOException,
            JsonProcessingException {
        if (millisFromEpoch != null && millisFromEpoch != 0L) {
            jgen.writeString(DateUtils.convertToISODateTime(millisFromEpoch));
        } else {
            jgen.writeNull();
        }
    }

}
