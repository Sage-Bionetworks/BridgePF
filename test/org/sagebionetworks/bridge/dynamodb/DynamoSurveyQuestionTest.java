package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.Unit;

public class DynamoSurveyQuestionTest {

    @Test
    public void canSerialize() throws Exception {
        DateTime date = DateTime.parse("2015-10-10T10:10:10.000Z");
        
        IntegerConstraints c = new IntegerConstraints();
        c.setMinValue(2.0d);
        c.setMaxValue(6.0d);
        c.setStep(2.0d);
        c.setUnit(Unit.DAYS);
        
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setTitle("Title");
        question.setPromptDetail("Prompt Detail");
        question.setPrompt("Prompt");
        question.setIdentifier("identifier");
        question.setGuid("AAA");
        question.setOrder(3);
        question.setSurveyKeyComponents("AAA", date.getMillis());
        question.setType("type");
        question.setUiHint(UIHint.CHECKBOX);
        question.setConstraints(c);
        
        String string = BridgeObjectMapper.get().writeValueAsString(question);
        assertEquals("{\"surveyCompoundKey\":\"AAA:1444471810000\",\"guid\":\"AAA\",\"identifier\":\"identifier\",\"type\":\"type\",\"title\":\"Title\",\"prompt\":\"Prompt\",\"promptDetail\":\"Prompt Detail\",\"constraints\":{\"dataType\":\"integer\",\"rules\":[],\"dataType\":\"integer\",\"unit\":\"days\",\"minValue\":2.0,\"maxValue\":6.0,\"step\":2.0,\"type\":\"IntegerConstraints\"},\"uiHint\":\"checkbox\"}", string);
        
        DynamoSurveyQuestion question2 = (DynamoSurveyQuestion)DynamoSurveyQuestion.fromJson(BridgeObjectMapper.get().readTree(string));
        assertEquals(question, question2);
    }
    
}
