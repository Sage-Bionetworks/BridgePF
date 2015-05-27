package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("unchecked")
public class SurveyElementTest {
    @Test
    public void serializeSurveyQuestion() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"fireEvent\":false,\n" +
                "   \"guid\":\"test-guid\",\n" +
                "   \"identifier\":\"test-survey-question\",\n" +
                "   \"prompt\":\"Is this a survey question?\",\n" +
                "   \"promptDetail\":\"Details about question\",\n" +
                "   \"type\":\"SurveyQuestion\",\n" +
                "   \"uiHint\":\"textfield\"\n" +
                "}";

        // convert to POJO
        DynamoSurveyQuestion question = (DynamoSurveyQuestion) BridgeObjectMapper.get().readValue(jsonText,
                SurveyElement.class);
        assertNull(question.getConstraints());
        assertNotNull(question.getData());
        assertFalse(question.getFireEvent());
        assertEquals("test-guid", question.getGuid());
        assertEquals("test-survey-question", question.getIdentifier());
        assertEquals(0, question.getOrder());
        assertEquals("Is this a survey question?", question.getPrompt());
        assertEquals("Details about question", question.getPromptDetail());
        assertNull(question.getSurveyCompoundKey());
        assertEquals("SurveyQuestion", question.getType());
        assertEquals(UIHint.TEXTFIELD, question.getUiHint());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(question);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(7, jsonMap.size());
        assertFalse((boolean) jsonMap.get("fireEvent"));
        assertEquals("test-guid", jsonMap.get("guid"));
        assertEquals("test-survey-question", jsonMap.get("identifier"));
        assertEquals("Is this a survey question?", jsonMap.get("prompt"));
        assertEquals("Details about question", jsonMap.get("promptDetail"));
        assertEquals("SurveyQuestion", jsonMap.get("type"));
        assertEquals("textfield", jsonMap.get("uiHint"));
    }

    @Test
    public void serializeSurveyInfoScreen() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"guid\":\"test-guid\",\n" +
                "   \"identifier\":\"test-survey-info-screen\",\n" +
                "   \"image\":{\n" +
                "       \"source\":\"http://www.example.com/test.png\",\n" +
                "       \"width\":200,\n" +
                "       \"height\":150\n" +
                "   },\n" +
                "   \"prompt\":\"This is the survey info\",\n" +
                "   \"promptDetail\":\"More info\",\n" +
                "   \"title\":\"Survey Info\",\n" +
                "   \"type\":\"SurveyInfoScreen\"\n" +
                "}";

        // convert to POJO
        DynamoSurveyInfoScreen infoScreen = (DynamoSurveyInfoScreen) BridgeObjectMapper.get().readValue(jsonText,
                SurveyElement.class);
        assertNotNull(infoScreen.getData());
        assertEquals("test-guid", infoScreen.getGuid());
        assertEquals("test-survey-info-screen", infoScreen.getIdentifier());
        assertEquals(0, infoScreen.getOrder());
        assertEquals("This is the survey info", infoScreen.getPrompt());
        assertEquals("More info", infoScreen.getPromptDetail());
        assertNull(infoScreen.getSurveyCompoundKey());
        assertEquals("Survey Info", infoScreen.getTitle());
        assertEquals("SurveyInfoScreen", infoScreen.getType());

        assertEquals("http://www.example.com/test.png", infoScreen.getImage().getSource());
        assertEquals(200, infoScreen.getImage().getWidth());
        assertEquals(150, infoScreen.getImage().getHeight());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(infoScreen);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(7, jsonMap.size());
        assertEquals("test-guid", jsonMap.get("guid"));
        assertEquals("test-survey-info-screen", jsonMap.get("identifier"));
        assertEquals("This is the survey info", jsonMap.get("prompt"));
        assertEquals("More info", jsonMap.get("promptDetail"));
        assertEquals("Survey Info", jsonMap.get("title"));
        assertEquals("SurveyInfoScreen", jsonMap.get("type"));

        Map<String, Object> imageMap = (Map<String, Object>) jsonMap.get("image");
        assertEquals(4, imageMap.size());
        assertEquals("http://www.example.com/test.png", imageMap.get("source"));
        assertEquals(200, imageMap.get("width"));
        assertEquals(150, imageMap.get("height"));
        assertEquals("Image", imageMap.get("type"));
    }

    @Test(expected = InvalidEntityException.class)
    public void serializeInvalidSurveyElementType() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"guid\":\"bad-guid\",\n" +
                "   \"identifier\":\"bad-survey-element\",\n" +
                "   \"prompt\":\"Is this valid?\",\n" +
                "   \"promptDetail\":\"Details about validity\",\n" +
                "   \"type\":\"SurveyEggplant\"\n" +
                "}";

        // convert to POJO
        BridgeObjectMapper.get().readValue(jsonText, SurveyElement.class);
    }
}
