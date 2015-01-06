package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("unchecked")
public class UploadSchemaTest {
    // TODO test more

    @Test
    public void testSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"name\":\"Test Schema\",\n" +
                "   \"revision\":3,\n" +
                "   \"schemaId\":\"test-schema\",\n" +
                "   \"studyId\":\"api\",\n" +
                "   \"fieldDefinitions\":[\n" +
                "       {\n" +
                "           \"name\":\"foo\",\n" +
                "           \"required\":true,\n" +
                "           \"type\":\"INT\"\n" +
                "       },\n" +
                "       {\n" +
                "           \"name\":\"bar\",\n" +
                "           \"required\":false,\n" +
                "           \"type\":\"STRING\"\n" +
                "       }\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        UploadSchema uploadSchema = BridgeObjectMapper.get().readValue(jsonText, UploadSchema.class);
        assertEquals("Test Schema", uploadSchema.getName());
        assertEquals(3, uploadSchema.getRevision());
        assertEquals("test-schema", uploadSchema.getSchemaId());
        assertEquals("api", uploadSchema.getStudyId());

        UploadFieldDefinition fooFieldDef = uploadSchema.getFieldDefinitions().get(0);
        assertEquals("foo", fooFieldDef.getName());
        assertTrue(fooFieldDef.isRequired());
        assertEquals(UploadFieldType.INT, fooFieldDef.getType());

        UploadFieldDefinition barFieldDef = uploadSchema.getFieldDefinitions().get(1);
        assertEquals("bar", barFieldDef.getName());
        assertFalse(barFieldDef.isRequired());
        assertEquals(UploadFieldType.STRING, barFieldDef.getType());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(uploadSchema);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(6, jsonMap.size());
        assertEquals("Test Schema", jsonMap.get("name"));
        assertEquals(3, jsonMap.get("revision"));
        assertEquals("test-schema", jsonMap.get("schemaId"));
        assertEquals("UploadSchema", jsonMap.get("type"));
        assertEquals("api", jsonMap.get("studyId"));

        List<Map<String, Object>> fieldDefJsonList = (List) jsonMap.get("fieldDefinitions");
        Map<String, Object> fooJsonMap = fieldDefJsonList.get(0);
        assertEquals("foo", fooJsonMap.get("name"));
        assertTrue((boolean) fooJsonMap.get("required"));
        assertEquals("INT", fooJsonMap.get("type"));

        Map<String, Object> barJsonMap = fieldDefJsonList.get(1);
        assertEquals("bar", barJsonMap.get("name"));
        assertFalse((boolean) barJsonMap.get("required"));
        assertEquals("STRING", barJsonMap.get("type"));
    }
}
