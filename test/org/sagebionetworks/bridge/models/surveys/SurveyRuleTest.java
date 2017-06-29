package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.TreeSet;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

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
        assertEquals("eq", node.get("operator").textValue());
        assertEquals("value", node.get("value").textValue());
        assertEquals("test", node.get("skipTo").textValue());
        assertNull(node.get("endSurvey"));
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(skipToRule, deser);
    }

    @Test
    public void canSerializeEndsurvey() throws Exception {
        SurveyRule endRule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue("value")
                .withEndSurvey(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(endRule);
        assertEquals("eq", node.get("operator").textValue());
        assertEquals("value", node.get("value").textValue());
        assertNull(node.get("skipTo"));
        assertTrue(node.get("endSurvey").booleanValue());
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(endRule, deser);
    }
    
    @Test
    public void canSerializeAlwaysRule() throws Exception {
        SurveyRule alwaysRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(alwaysRule);
        assertEquals("always", node.get("operator").textValue());
        assertNull(node.get("value"));
        assertNull(node.get("skipTo"));
        assertTrue(node.get("endSurvey").booleanValue());
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(alwaysRule, deser);
    }
    
    @Test
    public void canSerializeAssignDataGroupRule() throws Exception {
        SurveyRule dataGroupRule = new SurveyRule.Builder().withValue("foo").withOperator(Operator.EQ)
                .withAssignDataGroup("bar").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(dataGroupRule);
        assertEquals("eq", node.get("operator").textValue());
        assertEquals("foo", node.get("value").textValue());
        assertNull(node.get("skipTo"));
        assertEquals("bar", node.get("assignDataGroup").textValue());
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(dataGroupRule, deser);
    }
    
    @Test
    public void canSerializeDisplayIf() throws Exception {
        TreeSet<String> displayGroups = new TreeSet<>();
        displayGroups.add("bar");
        displayGroups.add("foo");
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(Operator.ANY)
                .withDataGroups(displayGroups).withDisplayIf(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(displayIf);
        assertEquals("any", node.get("operator").textValue());
        assertEquals("bar", node.get("dataGroups").get(0).textValue());
        assertEquals("foo", node.get("dataGroups").get(1).textValue());
        assertTrue(node.get("displayIf").booleanValue());
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(displayIf, deser);
    }
    
    @Test
    public void canSerializeDisplayUnless() throws Exception {
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(Operator.ANY)
                .withDataGroups(Sets.newHashSet("foo")).withDisplayUnless(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(displayIf);
        assertEquals("any", node.get("operator").textValue());
        assertEquals("foo", node.get("dataGroups").get(0).textValue());
        assertTrue(node.get("displayUnless").booleanValue());
        assertEquals("SurveyRule", node.get("type").textValue());
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(displayIf, deser);
    }
    
    // If the user sends a property set to false, ensure the field is set to null and
    // that serialization of the rule excludes that property. Only one boolean property
    // can be true at a time and only the true property will be in JSON representations.
    @Test
    public void sendingFalseDeserializesToNullProperty() throws Exception {
        String json = TestUtils.createJson("{'displayIf':false,"+
                "'displayUnless':false,'endSurvey':false}");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(json);
        assertNull(node.get("displayIf"));
        assertNull(node.get("displayUnless"));
        assertNull(node.get("endSurvey"));
    }
    
    @Test
    public void settingBooleanActionToFalseNullifiesIt() {
        SurveyRule.Builder builder = new SurveyRule.Builder().withDisplayIf(true).withDisplayUnless(true).withEndSurvey(true);
        
        SurveyRule rule = builder.withDisplayIf(false).withDisplayUnless(false).withEndSurvey(false).build();
        assertNull(rule.getDisplayIf());
        assertNull(rule.getDisplayUnless());
        assertNull(rule.getEndSurvey());
    }
}
