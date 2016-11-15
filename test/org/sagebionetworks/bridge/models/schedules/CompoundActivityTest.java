package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class CompoundActivityTest {
    @Test
    public void normalCase() {
        // Make mutable lists here, so we can test defensive copies later.
        List<SchemaReference> inputSchemaList = new ArrayList<>();
        SchemaReference fooSchema = new SchemaReference("foo", 2);
        inputSchemaList.add(fooSchema);
        SchemaReference barSchema = new SchemaReference("bar", 3);
        inputSchemaList.add(barSchema);

        List<SurveyReference> inputSurveyList = new ArrayList<>();
        SurveyReference asdfSurvey = new SurveyReference("asdf", "asdf-guid", null);
        inputSurveyList.add(asdfSurvey);
        SurveyReference jklSurvey = new SurveyReference("jkl", "jkl-guid", null);
        inputSurveyList.add(jklSurvey);

        CompoundActivity compoundActivity = new CompoundActivity.Builder().withSchemaList(inputSchemaList)
                .withSurveyList(inputSurveyList).withTaskIdentifier("combo-activity").build();
        assertEquals("combo-activity", compoundActivity.getTaskIdentifier());

        List<SchemaReference> outputSchemaList = compoundActivity.getSchemaList();
        assertEquals(2, outputSchemaList.size());
        assertEquals(fooSchema, outputSchemaList.get(0));
        assertEquals(barSchema, outputSchemaList.get(1));

        List<SurveyReference> outputSurveyList = compoundActivity.getSurveyList();
        assertEquals(2, outputSurveyList.size());
        assertEquals(asdfSurvey, outputSurveyList.get(0));
        assertEquals(jklSurvey, outputSurveyList.get(1));

        // toString() gives a lot of stuff and depends on two other classes. To make the tests robust and resilient to
        // changes in encapsulated classes, just test a few keywords
        String activityString = compoundActivity.toString();
        assertTrue(activityString.contains("combo-activity"));
        assertTrue(activityString.contains("foo"));
        assertTrue(activityString.contains("bar"));
        assertTrue(activityString.contains("asdf"));
        assertTrue(activityString.contains("jkl"));

        // modify original lists to verify we have a defensive copy
        inputSchemaList.add(new SchemaReference("third-schema", 4));
        assertEquals(2, outputSchemaList.size());

        inputSurveyList.add(new SurveyReference("third-survey", "third-survey-guid", null));
        assertEquals(2, outputSurveyList.size());

        // attempt to modify output list to verify immutability
        try {
            outputSchemaList.add(new SchemaReference("another", 5));
            fail("expected exception modifying outputSchemaList");
        } catch (UnsupportedOperationException ex) {
            // expected exception
        }

        try {
            outputSurveyList.add(new SurveyReference("another", "another-guid", null));
            fail("expected exception modifying outputSurveyList");
        } catch (UnsupportedOperationException ex) {
            // expected exception
        }
    }

    @Test
    public void nullProps() {
        // This is technically invalid, but this is validated by the validator. We need to make sure nothing crashes if
        // everything is null.
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withSchemaList(null).withSurveyList(null)
                .withTaskIdentifier(null).build();
        assertNull(compoundActivity.getTaskIdentifier());
        assertNotNull(compoundActivity.toString());

        // Verify the null lists are replaced with empty immutable lists.
        List<SchemaReference> outputSchemaList = compoundActivity.getSchemaList();
        assertTrue(outputSchemaList.isEmpty());
        try {
            outputSchemaList.add(new SchemaReference("another", 5));
            fail("expected exception modifying outputSchemaList");
        } catch (UnsupportedOperationException ex) {
            // expected exception
        }

        List<SurveyReference> outputSurveyList = compoundActivity.getSurveyList();
        assertTrue(outputSurveyList.isEmpty());
        try {
            outputSurveyList.add(new SurveyReference("another", "another-guid", null));
            fail("expected exception modifying outputSurveyList");
        } catch (UnsupportedOperationException ex) {
            // expected exception
        }
    }

    @Test
    public void jsonSerialization() throws Exception {
        // Make schema and survey refs so this test doesn't depend on those implementations.
        SchemaReference fooSchema = new SchemaReference("foo", 2);
        SchemaReference barSchema = new SchemaReference("bar", 3);
        SurveyReference asdfSurvey = new SurveyReference("asdf", "asdf-guid", null);
        SurveyReference jklSurvey = new SurveyReference("jkl", "jkl-guid", null);

        // start with JSON
        String jsonText = "{\n" +
                "   \"taskIdentifier\":\"json-activity\",\n" +
                "   \"schemaList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(fooSchema) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(barSchema) + "\n" +
                "   ],\n" +
                "   \"surveyList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(asdfSurvey) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(jklSurvey) + "\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        CompoundActivity compoundActivity = BridgeObjectMapper.get().readValue(jsonText, CompoundActivity.class);
        assertEquals("json-activity", compoundActivity.getTaskIdentifier());

        List<SchemaReference> outputSchemaList = compoundActivity.getSchemaList();
        assertEquals(2, outputSchemaList.size());
        assertEquals(fooSchema, outputSchemaList.get(0));
        assertEquals(barSchema, outputSchemaList.get(1));

        List<SurveyReference> outputSurveyList = compoundActivity.getSurveyList();
        assertEquals(2, outputSurveyList.size());
        assertEquals(asdfSurvey, outputSurveyList.get(0));
        assertEquals(jklSurvey, outputSurveyList.get(1));

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(compoundActivity, JsonNode.class);
        assertEquals(4, jsonNode.size());
        assertEquals("json-activity", jsonNode.get("taskIdentifier").textValue());
        assertEquals("CompoundActivity", jsonNode.get("type").textValue());

        // For the lists, this is already tested by the encapsulated classes. Just verify that we have a list of the
        // right size.
        assertTrue(jsonNode.get("schemaList").isArray());
        assertEquals(2, jsonNode.get("schemaList").size());

        assertTrue(jsonNode.get("surveyList").isArray());
        assertEquals(2, jsonNode.get("surveyList").size());
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(CompoundActivity.class).allFieldsShouldBeUsed().verify();
    }
}
