package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.Schedule.Type;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ScheduleTypeDeserializer extends JsonDeserializer<Schedule.Type> {

    @Override
    public Type deserialize(JsonParser parser, DeserializationContext arg1) throws IOException, JsonProcessingException {
        String op = parser.getText();
        return Schedule.Type.valueOf(op.toUpperCase());
    }

}
