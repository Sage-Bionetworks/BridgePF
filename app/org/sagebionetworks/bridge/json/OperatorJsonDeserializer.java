package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class OperatorJsonDeserializer extends JsonDeserializer<SurveyRule.Operator> {
    @Override
    public SurveyRule.Operator deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        String op = jp.getText();
        return SurveyRule.Operator.valueOf(op.toUpperCase());
    }
}
