package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.schedules.ActivityType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ActivityTypeDeserializer extends JsonDeserializer<ActivityType> {

    @Override
    public ActivityType deserialize(JsonParser parser, DeserializationContext arg1) throws IOException,
            JsonProcessingException {
        String value = parser.getText();
        return ActivityType.valueOf(value.toUpperCase());
    }

}
