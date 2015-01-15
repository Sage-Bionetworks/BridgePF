package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.surveys.Unit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class UnitDeserializer extends JsonDeserializer<Unit> {

    @Override
    public Unit deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
        String value = parser.getText();
        return Unit.valueOf(value.toUpperCase());
    }
    
    @Override
    public Unit getNullValue() {
        return null;
    }

}