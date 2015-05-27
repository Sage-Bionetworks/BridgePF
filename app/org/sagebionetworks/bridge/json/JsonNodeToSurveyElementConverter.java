package org.sagebionetworks.bridge.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.StdConverter;

import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;

/**
 * Jackson converter to convert JsonNodes into SurveyElements, calling through to the SurveyElementFactory. This,
 * combined with the JsonDeserialize annotation on SurveyElement, allows classes containing SurveyElement to no longer
 * have to implement custom fromJson() methods.
 */
public class JsonNodeToSurveyElementConverter extends StdConverter<JsonNode, SurveyElement> {
    @Override
    public SurveyElement convert(JsonNode jsonNode) {
        return SurveyElementFactory.fromJson(jsonNode);
    }
}
