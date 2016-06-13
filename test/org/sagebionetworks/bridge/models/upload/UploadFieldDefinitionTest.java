package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("ConstantConditions")
public class UploadFieldDefinitionTest {
    @Test
    public void testBuilder() {
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals("test-field", fieldDef.getName());
        assertTrue(fieldDef.isRequired());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDef.getType());
    }

    @Test
    public void testRequiredTrue() {
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withRequired(true).withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals("test-field", fieldDef.getName());
        assertTrue(fieldDef.isRequired());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDef.getType());
    }

    @Test
    public void testRequiredFalse() {
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withRequired(false).withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals("test-field", fieldDef.getName());
        assertFalse(fieldDef.isRequired());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDef.getType());
    }

    @Test
    public void testMultiChoiceAnswerList() {
        // set up original list
        List<String> originalAnswerList = new ArrayList<>();
        originalAnswerList.add("first");
        originalAnswerList.add("second");

        // build and validate
        List<String> expectedAnswerList = ImmutableList.of("first", "second");
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("multi-choice-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList(originalAnswerList).build();
        assertEquals(expectedAnswerList, fieldDef.getMultiChoiceAnswerList());

        // modify original list, verify that field def stays the same
        originalAnswerList.add("third");
        assertEquals(expectedAnswerList, fieldDef.getMultiChoiceAnswerList());
    }

    @Test
    public void testMultiChoiceAnswerVarargs() {
        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withName("multi-choice-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("aa", "bb", "cc").build();
        assertEquals(ImmutableList.of("aa", "bb", "cc"), fieldDef.getMultiChoiceAnswerList());
    }

    @Test
    public void testOptionalFields() {
        List<String> multiChoiceAnswerList = ImmutableList.of("foo", "bar", "baz");

        UploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition.Builder().withFileExtension(".test")
                .withMimeType("text/plain").withMinAppVersion(10).withMaxAppVersion(13).withMaxLength(128)
                .withMultiChoiceAnswerList(multiChoiceAnswerList).withName("optional-stuff").withRequired(false)
                .withType(UploadFieldType.STRING).build();
        assertEquals(".test", fieldDef.getFileExtension());
        assertEquals("text/plain", fieldDef.getMimeType());
        assertEquals(10, fieldDef.getMinAppVersion().intValue());
        assertEquals(13, fieldDef.getMaxAppVersion().intValue());
        assertEquals(128, fieldDef.getMaxLength().intValue());
        assertEquals(multiChoiceAnswerList, fieldDef.getMultiChoiceAnswerList());
        assertEquals("optional-stuff", fieldDef.getName());
        assertFalse(fieldDef.isRequired());
        assertEquals(UploadFieldType.STRING, fieldDef.getType());

        // Also test copy constructor.
        UploadFieldDefinition copy = new DynamoUploadFieldDefinition.Builder().copyOf(fieldDef).build();
        assertEquals(fieldDef, copy);
    }

    @Test
    public void testSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"fileExtension\":\".json\",\n" +
                "   \"mimeType\":\"text/json\",\n" +
                "   \"minAppVersion\":2,\n" +
                "   \"maxAppVersion\":7,\n" +
                "   \"maxLength\":24,\n" +
                "   \"multiChoiceAnswerList\":[\"asdf\", \"jkl;\"],\n" +
                "   \"name\":\"test-field\",\n" +
                "   \"required\":false,\n" +
                "   \"type\":\"INT\"\n" +
                "}";

        // convert to POJO
        List<String> expectedMultiChoiceAnswerList = ImmutableList.of("asdf", "jkl;");
        UploadFieldDefinition fieldDef = BridgeObjectMapper.get().readValue(jsonText, UploadFieldDefinition.class);
        assertEquals(".json", fieldDef.getFileExtension());
        assertEquals("text/json", fieldDef.getMimeType());
        assertEquals(2, fieldDef.getMinAppVersion().intValue());
        assertEquals(7, fieldDef.getMaxAppVersion().intValue());
        assertEquals(24, fieldDef.getMaxLength().intValue());
        assertEquals(expectedMultiChoiceAnswerList, fieldDef.getMultiChoiceAnswerList());
        assertEquals("test-field", fieldDef.getName());
        assertFalse(fieldDef.isRequired());
        assertEquals(UploadFieldType.INT, fieldDef.getType());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(fieldDef);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(9, jsonMap.size());
        assertEquals(".json", jsonMap.get("fileExtension"));
        assertEquals("text/json", jsonMap.get("mimeType"));
        assertEquals(2, jsonMap.get("minAppVersion"));
        assertEquals(7, jsonMap.get("maxAppVersion"));
        assertEquals(24, jsonMap.get("maxLength"));
        assertEquals(expectedMultiChoiceAnswerList, jsonMap.get("multiChoiceAnswerList"));
        assertEquals("test-field", jsonMap.get("name"));
        assertFalse((boolean) jsonMap.get("required"));
        assertEquals("int", jsonMap.get("type"));
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(DynamoUploadFieldDefinition.class).allFieldsShouldBeUsed().verify();
    }
}
