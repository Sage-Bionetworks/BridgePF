package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.DateConverter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public final class DateTimeJsonDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        System.out.println("ENTERING DESERIALIZATION DATE TIME");
        String date = jp.getText();
        System.out.println(date);
        System.out.println(DateConverter.convertMillisFromEpoch(date));
        return DateConverter.convertMillisFromEpoch(date);
    }

}
