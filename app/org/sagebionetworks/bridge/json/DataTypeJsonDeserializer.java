package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.surveys.DataType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class DataTypeJsonDeserializer extends JsonDeserializer<DataType> {

    @Override
    public DataType deserialize(JsonParser parser, DeserializationContext context) throws IOException,
            JsonProcessingException {
        String value = parser.getText();
        return DataType.valueOf(value.toUpperCase());
    }
    
    @Override
    public DataType getNullValue() {
        return null;
    }

}
