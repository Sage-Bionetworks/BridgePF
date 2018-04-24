package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.sagebionetworks.bridge.time.DateUtils;

public final class DateTimeToLongSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long millisFromEpoch, JsonGenerator jgen, SerializerProvider sp) throws IOException,
            JsonProcessingException {
        jgen.writeString(DateUtils.convertToISODateTime(millisFromEpoch));
    }
    
    @Override
    public boolean isEmpty(Long millisFromEpoch) {
        return (millisFromEpoch == null || millisFromEpoch == 0L);
    }
    
}
