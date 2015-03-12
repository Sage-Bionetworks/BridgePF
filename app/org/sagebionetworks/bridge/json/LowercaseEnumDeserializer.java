package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

@SuppressWarnings("serial")
class LowercaseEnumDeserializer extends StdScalarDeserializer<Enum<?>> {

    protected LowercaseEnumDeserializer(Class<Enum<?>> clazz) {
        super(clazz);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String text = jp.getText().toUpperCase();
        return (Enum<?>) Enum.valueOf((Class<Enum>)handledType(), text);
    }    
    
}
