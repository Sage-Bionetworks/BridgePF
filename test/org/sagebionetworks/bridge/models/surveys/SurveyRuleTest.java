package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyRuleTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(SurveyRule.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void cannotSetEndSurveyToFalse() throws Exception {
        String json = TestUtils.createJson("{'operator':'eq','value':'No',"+
                "'skipTo':'theend','endSurvey':false}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);
        assertNull(rule.getEndSurvey());
        assertEquals(Operator.EQ, rule.getOperator());
        assertEquals("theend", rule.getSkipToTarget());
        assertEquals("No", rule.getValue());
    }
    
    @Test
    public void canSerializeSkipTo() throws Exception {
        SurveyRule skipToRule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue("value")
                .withSkipToTarget("test").withEndSurvey(Boolean.FALSE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(skipToRule);
        assertEquals("eq", node.get("operator").asText());
        assertEquals("value", node.get("value").asText());
        assertEquals("test", node.get("skipTo").asText());
        assertNull(node.get("endSurvey"));
        assertEquals("SurveyRule", node.get("type").asText());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(skipToRule, deser);
    }

    @Test
    public void canSerializeEndsurvey() throws Exception {
        SurveyRule endRule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue("value")
                .withEndSurvey(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(endRule);
        assertEquals("eq", node.get("operator").asText());
        assertEquals("value", node.get("value").asText());
        assertNull(node.get("skipTo"));
        assertTrue(node.get("endSurvey").asBoolean());
        assertEquals("SurveyRule", node.get("type").asText());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(endRule, deser);
    }
}
