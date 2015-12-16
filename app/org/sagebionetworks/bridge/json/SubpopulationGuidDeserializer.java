package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class SubpopulationGuidDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String string, DeserializationContext context)
            throws IOException, JsonProcessingException {
        return SubpopulationGuid.create(string);
    }

}
