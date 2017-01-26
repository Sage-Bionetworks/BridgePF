package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

public class DynamoCompoundActivityDefinitionTest {
    private static final SchemaReference FOO_SCHEMA = new SchemaReference("foo", 2);
    private static final SchemaReference BAR_SCHEMA = new SchemaReference("bar", 3);
    private static final SurveyReference ASDF_SURVEY = new SurveyReference("asdf", "asdf-guid", null);
    private static final SurveyReference JKL_SURVEY = new SurveyReference("jkl", "jkl-guid", null);

    @Test
    public void immutableLists() {
        // create def - Lists are initially empty.
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        assertTrue(def.getSchemaList().isEmpty());
        assertListIsImmutable(def.getSchemaList(), FOO_SCHEMA);
        assertTrue(def.getSurveyList().isEmpty());
        assertListIsImmutable(def.getSurveyList(), ASDF_SURVEY);

        // create test mutable lists
        List<SchemaReference> originalSchemaList = Lists.newArrayList(FOO_SCHEMA);
        List<SurveyReference> originalSurveyList = Lists.newArrayList(ASDF_SURVEY);

        // set to non-empty lists
        def.setSchemaList(originalSchemaList);
        def.setSurveyList(originalSurveyList);

        // modify original lists
        originalSchemaList.add(BAR_SCHEMA);
        originalSurveyList.add(JKL_SURVEY);

        // verify that the lists in the def are unchanged
        assertEquals(1, def.getSchemaList().size());
        assertEquals(FOO_SCHEMA, def.getSchemaList().get(0));
        assertListIsImmutable(def.getSchemaList(), BAR_SCHEMA);
        assertEquals(1, def.getSurveyList().size());
        assertEquals(ASDF_SURVEY, def.getSurveyList().get(0));
        assertListIsImmutable(def.getSurveyList(), JKL_SURVEY);

        // set lists to null, validate that lists are still empty and immutable
        def.setSchemaList(null);
        def.setSurveyList(null);
        assertTrue(def.getSchemaList().isEmpty());
        assertListIsImmutable(def.getSchemaList(), FOO_SCHEMA);
        assertTrue(def.getSurveyList().isEmpty());
        assertListIsImmutable(def.getSurveyList(), ASDF_SURVEY);
    }

    @Test
    public void serialize() throws Exception {
        // Use schema and survey refs so this test doesn't depend on those implementations.

        // start with JSON
        String jsonText = "{\n" +
                "   \"studyId\":\"test-study\",\n" +
                "   \"taskId\":\"test-task\",\n" +
                "   \"schemaList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(FOO_SCHEMA) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(BAR_SCHEMA) + "\n" +
                "   ],\n" +
                "   \"surveyList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(ASDF_SURVEY) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(JKL_SURVEY) + "\n" +
                "   ],\n" +
                "   \"version\":42\n" +
                "}";

        // convert to POJO - deserialize it as the base type, so we know it works with the base type
        DynamoCompoundActivityDefinition def = (DynamoCompoundActivityDefinition) BridgeObjectMapper.get().readValue(
                jsonText, CompoundActivityDefinition.class);
        assertEquals("test-study", def.getStudyId());
        assertEquals("test-task", def.getTaskId());
        assertEquals(42, def.getVersion().longValue());

        List<SchemaReference> outputSchemaList = def.getSchemaList();
        assertEquals(2, outputSchemaList.size());
        assertEquals(FOO_SCHEMA, outputSchemaList.get(0));
        assertEquals(BAR_SCHEMA, outputSchemaList.get(1));

        List<SurveyReference> outputSurveyList = def.getSurveyList();
        assertEquals(2, outputSurveyList.size());
        assertEquals(ASDF_SURVEY, outputSurveyList.get(0));
        assertEquals(JKL_SURVEY, outputSurveyList.get(1));

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(def, JsonNode.class);
        assertEquals(6, jsonNode.size());
        assertEquals("test-study", jsonNode.get("studyId").textValue());
        assertEquals("test-task", jsonNode.get("taskId").textValue());
        assertEquals(42, jsonNode.get("version").intValue());
        assertEquals("CompoundActivityDefinition", jsonNode.get("type").textValue());

        // For the lists, this is already tested by the encapsulated classes. Just verify that we have a list of the
        // right size.
        assertTrue(jsonNode.get("schemaList").isArray());
        assertEquals(2, jsonNode.get("schemaList").size());

        assertTrue(jsonNode.get("surveyList").isArray());
        assertEquals(2, jsonNode.get("surveyList").size());

        // convert to JSON using the PUBLIC_DEFINITION_WRITER
        // For simplicity, just test that study ID is absent and the other major key values are present
        String publicJsonText = CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(def);
        JsonNode publicJsonNode = BridgeObjectMapper.get().readTree(publicJsonText);
        assertNull(publicJsonNode.get("studyId"));
        assertEquals("test-task", publicJsonNode.get("taskId").textValue());
        assertEquals("CompoundActivityDefinition", publicJsonNode.get("type").textValue());
    }

    private static <T> void assertListIsImmutable(List<T> list, T objToAdd) {
        try {
            list.add(objToAdd);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }
}
