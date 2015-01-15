package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.surveys.DurationUnit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class DurationUnitDeserializer extends JsonDeserializer<DurationUnit> {

    @Override
    public DurationUnit deserialize(JsonParser parser, DeserializationContext context) throws IOException,
            JsonProcessingException {
        String value = parser.getText();
        return DurationUnit.valueOf(value.toUpperCase());
    }
    
    @Override
    public DurationUnit getNullValue() {
        return null;
    }

}
