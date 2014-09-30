package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ScheduleTypeDeserializer extends JsonDeserializer<ScheduleType> {

    @Override
    public ScheduleType deserialize(JsonParser parser, DeserializationContext arg1) throws IOException, JsonProcessingException {
        String op = parser.getText();
        return ScheduleType.valueOf(op.toUpperCase());
    }

}
