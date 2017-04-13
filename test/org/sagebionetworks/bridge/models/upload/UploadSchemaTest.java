package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.AppVersionHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("unchecked")
public class UploadSchemaTest {
    @Test
    public void fieldDefList() {
        UploadSchema schema = UploadSchema.create();

        // make field for test
        UploadFieldDefinition fieldDef1 = new UploadFieldDefinition.Builder().withName("test-field-1")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        UploadFieldDefinition fieldDef2 = new UploadFieldDefinition.Builder().withName("test-field-2")
                .withType(UploadFieldType.INT).build();

        // field def list starts out empty
        assertTrue(schema.getFieldDefinitions().isEmpty());
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());

        // set the field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(fieldDef1);

        schema.setFieldDefinitions(fieldDefList);
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());
        {
            List<UploadFieldDefinition> gettedFieldDefList = schema.getFieldDefinitions();
            assertEquals(1, gettedFieldDefList.size());
            assertEquals(fieldDef1, gettedFieldDefList.get(0));
        }

        // Modify the original list. getFieldDefinitions() shouldn't reflect this change.
        fieldDefList.add(fieldDef2);
        {
            List<UploadFieldDefinition> gettedFieldDefList = schema.getFieldDefinitions();
            assertEquals(1, gettedFieldDefList.size());
            assertEquals(fieldDef1, gettedFieldDefList.get(0));
        }

        // Set field def list to null. It'll come back as empty.
        schema.setFieldDefinitions(null);
        assertTrue(schema.getFieldDefinitions().isEmpty());
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());
    }

    private static void assertFieldDefListIsImmutable(List<UploadFieldDefinition> fieldDefList) {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("added-field")
                .withType(UploadFieldType.BOOLEAN).build();
        try {
            fieldDefList.add(fieldDef);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    @Test
    public void getKeyFromStudyAndSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("test");
        assertEquals("api:test", ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromNullStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromEmptyStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("");
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromBlankStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("   ");
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromNullSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromBlankSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("   ");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getStudyAndSchemaFromKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api:test");
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
        ddbUploadSchema.setKey(":test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyWithEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api:");
    }

    @Test
    public void getKeyWithColonsInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("api");
        ddbUploadSchema.setSchemaId("test:schema");
        assertEquals("api:test:schema", ddbUploadSchema.getKey());
    }

    @Test
    public void setKeyWithColonsInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api:test:schema");
        assertEquals("api", ddbUploadSchema.getStudyId());
        assertEquals("test:schema", ddbUploadSchema.getSchemaId());
    }

    @Test
    public void getSetMinMaxAppVersions() throws Exception {
        AppVersionHelper.testAppVersionHelper(DynamoUploadSchema.class);
    }

    @Test
    public void schemaKeyObject() {
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setStudyId("test-study");
        schema.setSchemaId("test-schema");
        schema.setRevision(7);
        assertEquals("test-study-test-schema-v7", schema.getSchemaKey().toString());
    }

    @Test
    public void testSerialization() throws Exception {
        String surveyCreatedOnStr = "2016-04-27T19:00:00.002-0700";
        long surveyCreatedOnMillis = DateTime.parse(surveyCreatedOnStr).getMillis();

        // start with JSON. Some field definitions may already be serialized using upper-case enums
        // so leave this test string as it is. We know from other tests that lower-case 
        // strings work.
        String jsonText = "{\n" +
                "   \"maxAppVersions\":{\"iOS\":37, \"Android\":42},\n" +
                "   \"minAppVersions\":{\"iOS\":13, \"Android\":23},\n" +
                "   \"name\":\"Test Schema\",\n" +
                "   \"revision\":3,\n" +
                "   \"schemaId\":\"test-schema\",\n" +
                "   \"schemaType\":\"ios_survey\",\n" +
                "   \"studyId\":\"test-study\",\n" +
                "   \"surveyGuid\":\"survey-guid\",\n" +
                "   \"surveyCreatedOn\":\"" + surveyCreatedOnStr + "\",\n" +
                "   \"version\":6,\n" +
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
        assertEquals(UploadSchemaType.IOS_SURVEY, uploadSchema.getSchemaType());
        assertEquals("test-study", uploadSchema.getStudyId());
        assertEquals("survey-guid", uploadSchema.getSurveyGuid());
        assertEquals(surveyCreatedOnMillis, uploadSchema.getSurveyCreatedOn().longValue());
        assertEquals(6, ((DynamoUploadSchema) uploadSchema).getVersion().longValue());

        assertEquals(ImmutableSet.of("iOS", "Android"), uploadSchema.getAppVersionOperatingSystems());
        assertEquals(13, uploadSchema.getMinAppVersion("iOS").intValue());
        assertEquals(37, uploadSchema.getMaxAppVersion("iOS").intValue());
        assertEquals(23, uploadSchema.getMinAppVersion("Android").intValue());
        assertEquals(42, uploadSchema.getMaxAppVersion("Android").intValue());

        UploadFieldDefinition fooFieldDef = uploadSchema.getFieldDefinitions().get(0);
        assertEquals("foo", fooFieldDef.getName());
        assertTrue(fooFieldDef.isRequired());
        assertEquals(UploadFieldType.INT, fooFieldDef.getType());

        UploadFieldDefinition barFieldDef = uploadSchema.getFieldDefinitions().get(1);
        assertEquals("bar", barFieldDef.getName());
        assertFalse(barFieldDef.isRequired());
        assertEquals(UploadFieldType.STRING, barFieldDef.getType());

        // Add study ID and verify that it doesn't get leaked into the JSON
        uploadSchema.setStudyId("test-study");

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(uploadSchema);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(12, jsonMap.size());
        assertEquals("Test Schema", jsonMap.get("name"));
        assertEquals(3, jsonMap.get("revision"));
        assertEquals("test-schema", jsonMap.get("schemaId"));
        assertEquals("ios_survey", jsonMap.get("schemaType"));
        assertEquals("test-study", jsonMap.get("studyId"));
        assertEquals("survey-guid", jsonMap.get("surveyGuid"));
        assertEquals("UploadSchema", jsonMap.get("type"));
        assertEquals(6,  jsonMap.get("version"));

        Map<String, Integer> maxAppVersionMap = (Map<String, Integer>) jsonMap.get("maxAppVersions");
        assertEquals(2, maxAppVersionMap.size());
        assertEquals(37, maxAppVersionMap.get("iOS").intValue());
        assertEquals(42, maxAppVersionMap.get("Android").intValue());

        Map<String, Integer> minAppVersionMap = (Map<String, Integer>) jsonMap.get("minAppVersions");
        assertEquals(2, minAppVersionMap.size());
        assertEquals(13, minAppVersionMap.get("iOS").intValue());
        assertEquals(23, minAppVersionMap.get("Android").intValue());

        // The createdOn time is converted into ISO timestamp, but might be in a different timezone. Ensure that it
        // still refers to the correct instant in time, down to the millisecond.
        long resultSurveyCreatedOnMillis = DateTime.parse((String) jsonMap.get("surveyCreatedOn")).getMillis();
        assertEquals(surveyCreatedOnMillis, resultSurveyCreatedOnMillis);

        List<Map<String, Object>> fieldDefJsonList = (List<Map<String, Object>>) jsonMap.get("fieldDefinitions");
        assertEquals(2, fieldDefJsonList.size());

        Map<String, Object> fooJsonMap = fieldDefJsonList.get(0);
        assertEquals("foo", fooJsonMap.get("name"));
        assertTrue((boolean) fooJsonMap.get("required"));
        assertEquals("int", fooJsonMap.get("type"));

        Map<String, Object> barJsonMap = fieldDefJsonList.get(1);
        assertEquals("bar", barJsonMap.get("name"));
        assertFalse((boolean) barJsonMap.get("required"));
        assertEquals("string", barJsonMap.get("type"));

        // Serialize it again using the public writer, which includes all fields except studyId.
        String publicJson = UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema);
        Map<String, Object> publicJsonMap = BridgeObjectMapper.get().readValue(publicJson, JsonUtils.TYPE_REF_RAW_MAP);

        // Public JSON is missing studyId, but is otherwise identical to the non-public (internal worker) JSON.
        assertFalse(publicJsonMap.containsKey("studyId"));
        publicJsonMap.put("studyId", "test-study");
        assertEquals(jsonMap, publicJsonMap);
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
        List<UploadFieldDefinition> fieldDefList = fieldDefListMarshaller.unconvert(jsonText);
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
        String marshalledJson = fieldDefListMarshaller.convert(fieldDefList);

        // then convert to a list so we can validate the raw JSON
        List<Map<String, Object>> fieldDefJsonList = BridgeObjectMapper.get().readValue(marshalledJson,
                List.class);
        assertEquals(2, fieldDefJsonList.size());

        Map<String, Object> fooJsonMap = fieldDefJsonList.get(0);
        assertEquals("foo", fooJsonMap.get("name"));
        assertTrue((boolean) fooJsonMap.get("required"));
        assertEquals("int", fooJsonMap.get("type"));

        Map<String, Object> barJsonMap = fieldDefJsonList.get(1);
        assertEquals("bar", barJsonMap.get("name"));
        assertFalse((boolean) barJsonMap.get("required"));
        assertEquals("string", barJsonMap.get("type"));
    }
}
