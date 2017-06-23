package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

@SuppressWarnings("unchecked")
public class SurveyElementTest {
    
    private static final SurveyRule BEFORE_RULE = new SurveyRule.Builder().withOperator(Operator.EQ).withValue(10)
            .withAssignDataGroup("foo").build();
    private static final SurveyRule AFTER_RULE = new SurveyRule.Builder().withEndSurvey(true)
            .withOperator(Operator.ALWAYS).build();
    
    @Test
    public void serializeSurveyQuestion() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'fireEvent':false," +
                "'guid':'test-guid'," +
                "'identifier':'test-survey-question'," +
                "'prompt':'Is this a survey question?'," +
                "'promptDetail':'Details about question'," +
                "'beforeRules':[{'operator':'eq','value':10,'assignDataGroup':'foo'}],"+
                "'afterRules':[{'operator':'always','endSurvey':true}],"+
                "'type':'SurveyQuestion'," +
                "'uiHint':'textfield'}");

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
        
        assertEquals(BEFORE_RULE, question.getBeforeRules().get(0));
        assertEquals(AFTER_RULE, question.getAfterRules().get(0));

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(question);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(9, jsonMap.size());
        assertFalse((boolean) jsonMap.get("fireEvent"));
        assertEquals("test-guid", jsonMap.get("guid"));
        assertEquals("test-survey-question", jsonMap.get("identifier"));
        assertEquals("Is this a survey question?", jsonMap.get("prompt"));
        assertEquals("Details about question", jsonMap.get("promptDetail"));
        assertEquals("SurveyQuestion", jsonMap.get("type"));
        assertEquals("textfield", jsonMap.get("uiHint"));
        
        SurveyQuestion deserQuestion = BridgeObjectMapper.get().readValue(convertedJson, SurveyQuestion.class);
        
        assertFalse(deserQuestion.getFireEvent());
        assertEquals("test-guid", deserQuestion.getGuid());
        assertEquals("test-survey-question", deserQuestion.getIdentifier());
        assertEquals("Is this a survey question?", deserQuestion.getPrompt());
        assertEquals("Details about question", deserQuestion.getPromptDetail());
        assertEquals("SurveyQuestion", deserQuestion.getType());
        assertEquals(UIHint.TEXTFIELD, deserQuestion.getUiHint());
        assertEquals(BEFORE_RULE, deserQuestion.getBeforeRules().get(0));
        assertEquals(AFTER_RULE, deserQuestion.getAfterRules().get(0));
    }

    @Test
    public void serializeSurveyInfoScreen() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'guid':'test-guid'," +
                "'identifier':'test-survey-info-screen'," +
                "'image':{" +
                "    'source':'http://www.example.com/test.png'," +
                "    'width':200," +
                "    'height':150" +
                "}," +
                "'prompt':'This is the survey info'," +
                "'beforeRules':[{'operator':'eq','value':10,'assignDataGroup':'foo'}],"+
                "'afterRules':[{'operator':'always','endSurvey':true}],"+
                "'promptDetail':'More info'," +
                "'title':'Survey Info'," +
                "'type':'SurveyInfoScreen'}");

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
        assertEquals(BEFORE_RULE, infoScreen.getBeforeRules().get(0));
        assertEquals(AFTER_RULE, infoScreen.getAfterRules().get(0));

        assertEquals("http://www.example.com/test.png", infoScreen.getImage().getSource());
        assertEquals(200, infoScreen.getImage().getWidth());
        assertEquals(150, infoScreen.getImage().getHeight());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(infoScreen);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(9, jsonMap.size());
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
        String jsonText = TestUtils.createJson("{'guid': 'bad-guid'," +
                "'identifier': 'bad-survey-element'," +
                "'prompt': 'Is this valid?'," +
                "'promptDetail': 'Details about validity'," +
                "'type': 'SurveyEggplant'}");

        // convert to POJO
        BridgeObjectMapper.get().readValue(jsonText, SurveyElement.class);
    }
}
