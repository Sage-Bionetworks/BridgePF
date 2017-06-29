package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSurveyInfoScreenTest {
    
    @Test
    public void copyConstructor() {
        SurveyInfoScreen screen = SurveyInfoScreen.create();
        screen.setPrompt("prompt");
        screen.setPromptDetail("promptDetail");
        screen.setTitle("title");
        screen.setImage(new Image("sourceUrl", 100, 100));
        screen.setSurveyCompoundKey("surveyCompoundKey");
        screen.setGuid("guid");
        screen.setIdentifier("identifier");
        screen.setType("SurveyInfoScreen");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        screen.setBeforeRules(Lists.newArrayList(beforeRule));
        screen.setAfterRules(Lists.newArrayList(afterRule));

        SurveyInfoScreen copy = new DynamoSurveyInfoScreen(screen);
        assertEquals("prompt", copy.getPrompt());
        assertEquals("promptDetail", copy.getPromptDetail());
        assertEquals("title", copy.getTitle());
        assertEquals(screen.getImage(), copy.getImage());
        assertEquals("surveyCompoundKey", copy.getSurveyCompoundKey());
        assertEquals("guid", copy.getGuid());
        assertEquals("identifier", copy.getIdentifier());
        assertEquals("SurveyInfoScreen", copy.getType());
        assertEquals(1, copy.getBeforeRules().size());
        assertEquals(beforeRule, copy.getBeforeRules().get(0));
        assertEquals(1, copy.getAfterRules().size());
        assertEquals(afterRule, copy.getAfterRules().get(0));
    }
}
