package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.dao.ParticipantOption;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ScopeOfSharingDeserializer extends JsonDeserializer<ParticipantOption.ScopeOfSharing> {
    @Override
    public ParticipantOption.ScopeOfSharing deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String op = jp.getText();
        return ParticipantOption.ScopeOfSharing.valueOf(op.toUpperCase());
    }

}
