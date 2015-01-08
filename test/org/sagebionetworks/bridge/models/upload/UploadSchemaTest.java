package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("unchecked")
public class UploadSchemaTest {
    @Test
    public void getKeyFromStudyAndSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("test");
        assertEquals("api-test", ddbUploadSchema.getKey());
    }

    @Test(expected = InvalidEntityException.class)
    public void getKeyFromNullStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setSchemaId("test");
        ddbUploadSchema.getKey();
    }

    @Test(expected = InvalidEntityException.class)
    public void getKeyFromEmptyStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("");
        ddbUploadSchema.setSchemaId("test");
        ddbUploadSchema.getKey();
    }

    @Test(expected = InvalidEntityException.class)
    public void getKeyFromNullSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.getKey();
    }

    @Test(expected = InvalidEntityException.class)
    public void getKeyFromEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("");
        ddbUploadSchema.getKey();
    }

    @Test
    public void getStudyAndSchemaFromKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api-test");
        assertEquals("api", ddbUploadSchema.getStudyId());
        assertEquals("test", ddbUploadSchema.getSchemaId());
    }

    @Test(expected = NullPointerException.class)
    public void nullKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyWithOnePart() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("keyWithOnePart");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyWithEmptyStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("-test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyWithEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api-");
    }

    @Test
    public void getKeyWithDashesInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("test-schema");
        assertEquals("api-test-schema", ddbUploadSchema.getKey());
    }

    @Test
    public void setKeyWithDashesInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api-test-schema");
        assertEquals("api", ddbUploadSchema.getStudyId());
        assertEquals("test-schema", ddbUploadSchema.getSchemaId());
    }

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
        assertEquals(2, fieldDefJsonList.size());

        Map<String, Object> fooJsonMap = fieldDefJsonList.get(0);
        assertEquals("foo", fooJsonMap.get("name"));
        assertTrue((boolean) fooJsonMap.get("required"));
        assertEquals("INT", fooJsonMap.get("type"));

        Map<String, Object> barJsonMap = fieldDefJsonList.get(1);
        assertEquals("bar", barJsonMap.get("name"));
        assertFalse((boolean) barJsonMap.get("required"));
        assertEquals("STRING", barJsonMap.get("type"));
    }

    @Test
    public void testDynamoDbFieldDefListMarshaller() throws Exception {
        DynamoUploadSchema.FieldDefinitionListMarshaller fieldDefListMarshaller =
                new DynamoUploadSchema.FieldDefinitionListMarshaller();

        // start with JSON
        String jsonText = "[\n" +
                "   {\n" +
                "       \"name\":\"foo\",\n" +
                "       \"required\":true,\n" +
                "       \"type\":\"INT\"\n" +
                "   },\n" +
                "   {\n" +
                "       \"name\":\"bar\",\n" +
                "       \"required\":false,\n" +
                "       \"type\":\"STRING\"\n" +
                "   }\n" +
                "]";

        // unmarshal and validate
        // Note that the first argument is supposed to be of type Class<List<UploadFileDefinition>>. Unfortunately,
        // there is no way to actually create a class of that type. Fortunately, the unmarshaller never uses that
        // object, so we just pass in null.
        List<UploadFieldDefinition> fieldDefList = fieldDefListMarshaller.unmarshall(null, jsonText);
        assertEquals(2, fieldDefList.size());

        UploadFieldDefinition fooFieldDef = fieldDefList.get(0);
        assertEquals("foo", fooFieldDef.getName());
        assertTrue(fooFieldDef.isRequired());
        assertEquals(UploadFieldType.INT, fooFieldDef.getType());

        UploadFieldDefinition barFieldDef = fieldDefList.get(1);
        assertEquals("bar", barFieldDef.getName());
        assertFalse(barFieldDef.isRequired());
        assertEquals(UploadFieldType.STRING, barFieldDef.getType());

        // re-marshall
        String marshalledJson = fieldDefListMarshaller.marshall(fieldDefList);

        // then convert to a list so we can validate the raw JSON
        List<Map<String, Object>> fieldDefJsonList = JsonUtils.INTERNAL_OBJECT_MAPPER.readValue(marshalledJson,
                List.class);
        assertEquals(2, fieldDefJsonList.size());

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
