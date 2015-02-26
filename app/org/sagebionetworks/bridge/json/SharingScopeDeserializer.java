package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class SharingScopeDeserializer extends JsonDeserializer<SharingScope> {
    @Override
    public SharingScope deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String op = jp.getText();
        return SharingScope.valueOf(op.toUpperCase());
    }

}
