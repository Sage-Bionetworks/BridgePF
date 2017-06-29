package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.Unit;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSurveyQuestionTest {
    
    @Test
    public void copyConstructor() {
        SurveyQuestion question = SurveyQuestion.create();
        question.setPrompt("prompt");
        question.setPromptDetail("promptDetail");
        question.setFireEvent(true);
        question.setUiHint(UIHint.COMBOBOX);
        question.setConstraints(new IntegerConstraints());
        question.setSurveyCompoundKey("surveyCompoundKey");
        question.setGuid("guid");
        question.setIdentifier("identifier");
        question.setType("SurveyQuestion");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        question.setBeforeRules(Lists.newArrayList(beforeRule));
        question.setAfterRules(Lists.newArrayList(afterRule));
    
        SurveyQuestion copy = new DynamoSurveyQuestion(question);
        assertEquals("prompt", copy.getPrompt());
        assertEquals("promptDetail", copy.getPromptDetail());
        assertTrue(copy.getFireEvent());
        assertEquals(UIHint.COMBOBOX, copy.getUiHint());
        assertTrue(copy.getConstraints() instanceof IntegerConstraints);
        assertEquals("surveyCompoundKey", copy.getSurveyCompoundKey());
        assertEquals("guid", copy.getGuid());
        assertEquals("identifier", copy.getIdentifier());
        assertEquals("SurveyQuestion", copy.getType());
        assertEquals(1, copy.getBeforeRules().size());
        assertEquals(question.getBeforeRules().get(0), copy.getBeforeRules().get(0));
        assertEquals(1, copy.getAfterRules().size());
        assertEquals(question.getAfterRules().get(0), copy.getAfterRules().get(0));
    }
    
    @Test
    public void canSerialize() throws Exception {
        DateTime date = DateTime.parse("2015-10-10T10:10:10.000Z");
        
        IntegerConstraints c = new IntegerConstraints();
        c.setMinValue(2.0d);
        c.setMaxValue(6.0d);
        c.setStep(2.0d);
        c.setUnit(Unit.DAYS);
        
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
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
        assertEquals("{\"surveyCompoundKey\":\"AAA:1444471810000\",\"guid\":\"AAA\",\"identifier\":\"identifier\",\"type\":\"type\",\"prompt\":\"Prompt\",\"promptDetail\":\"Prompt Detail\",\"fireEvent\":false,\"constraints\":{\"rules\":[],\"dataType\":\"integer\",\"unit\":\"days\",\"minValue\":2.0,\"maxValue\":6.0,\"step\":2.0,\"type\":\"IntegerConstraints\"},\"uiHint\":\"checkbox\"}", string);

        SurveyQuestion question2 = (SurveyQuestion)SurveyQuestion.fromJson(BridgeObjectMapper.get().readTree(string));
        assertEquals(question.getPromptDetail(), question2.getPromptDetail());
        assertEquals(question.getPrompt(), question2.getPrompt());
        assertEquals(question.getIdentifier(), question2.getIdentifier());
        assertEquals(question.getGuid(), question2.getGuid());
        assertEquals(question.getType(), question2.getType());
        assertEquals(question.getUiHint(), question2.getUiHint());
        
        IntegerConstraints c2 = (IntegerConstraints)question2.getConstraints();
        assertEquals(c.getMinValue(), c2.getMinValue());
        assertEquals(c.getMaxValue(), c2.getMaxValue());
        assertEquals(c.getStep(), c2.getStep());
        assertEquals(c.getUnit(), c2.getUnit());
    }
    
}
