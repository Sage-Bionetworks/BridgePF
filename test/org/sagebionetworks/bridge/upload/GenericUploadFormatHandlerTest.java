package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class GenericUploadFormatHandlerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final String SCHEMA_NAME = "Test Schema";
    private static final int SCHEMA_REV = 3;

    private static final String CREATED_ON_STRING = "2017-09-26T01:10:21.173+0900";
    private static final long CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(CREATED_ON_STRING);
    private static final String CREATED_ON_TIMEZONE = "+0900";

    private GenericUploadFormatHandler handler;
    private UploadSchemaService mockSchemaService;

    @Before
    public void setup() {
        mockSchemaService = mock(UploadSchemaService.class);
        handler = new GenericUploadFormatHandler();
        handler.setUploadSchemaService(mockSchemaService);
    }

    @Test
    public void test() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("record.json.my-int").withType(UploadFieldType.INT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.sanitize____field")
                        .withType(UploadFieldType.INT).build(),
                new UploadFieldDefinition.Builder().withName("record.json.my-attachment")
                        .withType(UploadFieldType.ATTACHMENT_V2).withFileExtension(".json").withMimeType("text/json")
                        .build(),
                new UploadFieldDefinition.Builder().withName("nonJson.txt").withType(UploadFieldType.ATTACHMENT_V2)
                        .withFileExtension(".txt").withMimeType("text/plain").build(),
                new UploadFieldDefinition.Builder().withName("sanitize____attachment.txt")
                        .withType(UploadFieldType.ATTACHMENT_V2).withFileExtension(".txt").withMimeType("text/plain")
                        .build());
        mockSchemaServiceWithFields(fieldDefList);

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"my-int\":17,\n" +
                "   \"sanitize!@#$field\":42,\n" +
                "   \"my-attachment\":{\"my-key\":\"my-value\"}\n" +
                "}";
        JsonNode recordJsonNode = BridgeObjectMapper.get().readTree(recordJsonText);

        byte[] nonJsonTxtContent = "Non-JSON attachment".getBytes();
        byte[] sanitizeAttachmentTxtContent = "Sanitize my filename".getBytes();

        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>builder().put("info.json", makeInfoJson())
                .put("record.json", recordJsonNode).build();
        Map<String, byte[]> unzippedDataMap = ImmutableMap.<String, byte[]>builder()
                .put("nonJson.txt", nonJsonTxtContent).put("sanitize!@#$attachment.txt", sanitizeAttachmentTxtContent)
                .build();

        UploadValidationContext context = makeContextWithContent(jsonDataMap, unzippedDataMap);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(2, dataMap.size());
        assertEquals(17, dataMap.get("record.json.my-int").intValue());
        assertEquals(42, dataMap.get("record.json.sanitize____field").intValue());

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(3, attachmentMap.size());
        assertEquals(nonJsonTxtContent, attachmentMap.get("nonJson.txt"));
        assertEquals(sanitizeAttachmentTxtContent, attachmentMap.get("sanitize____attachment.txt"));

        JsonNode jsonAttachmentNode = BridgeObjectMapper.get().readTree(attachmentMap.get(
                "record.json.my-attachment"));
        assertEquals(1, jsonAttachmentNode.size());
        assertEquals("my-value", jsonAttachmentNode.get("my-key").textValue());
    }

    @Test
    public void withDataFilename() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("foo").withType(UploadFieldType.STRING).withMaxLength(24)
                        .build(),
                new UploadFieldDefinition.Builder().withName("bar").withType(UploadFieldType.STRING).withMaxLength(24)
                        .build(),
                new UploadFieldDefinition.Builder().withName("additional.json.foo").withType(UploadFieldType.STRING)
                        .withMaxLength(24).build(),
                new UploadFieldDefinition.Builder().withName("additional.json.bar").withType(UploadFieldType.STRING)
                        .withMaxLength(24).build(),
                new UploadFieldDefinition.Builder().withName("attachment.json").withType(UploadFieldType.ATTACHMENT_V2)
                        .withFileExtension(".json").withMimeType("text/json").build());
        mockSchemaServiceWithFields(fieldDefList);

        // Setup inputs.
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");

        ObjectNode recordJsonNode = BridgeObjectMapper.get().createObjectNode();
        recordJsonNode.put("foo", "foo-value");
        recordJsonNode.put("bar", "bar-value");

        ObjectNode additionalJsonNode = BridgeObjectMapper.get().createObjectNode();
        additionalJsonNode.put("foo", "additional-foo-value");
        additionalJsonNode.put("bar", "additional-bar-value");

        ObjectNode attachmentJsonNode = BridgeObjectMapper.get().createObjectNode();
        attachmentJsonNode.put("foo", "foo-attachment-value");
        attachmentJsonNode.put("bar", "bar-attachment-value");

        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>builder().put("info.json", infoJsonNode)
                .put("record.json", recordJsonNode).put("additional.json", additionalJsonNode)
                .put("attachment.json", attachmentJsonNode).build();

        UploadValidationContext context = makeContextWithContent(jsonDataMap, ImmutableMap.of());

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(4, dataMap.size());
        assertEquals("foo-value", dataMap.get("foo").textValue());
        assertEquals("bar-value", dataMap.get("bar").textValue());
        assertEquals("additional-foo-value", dataMap.get("additional.json.foo").textValue());
        assertEquals("additional-bar-value", dataMap.get("additional.json.bar").textValue());

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(1, attachmentMap.size());
        JsonNode outputAttachmentJsonNode = BridgeObjectMapper.get().readTree(attachmentMap.get("attachment.json"));
        assertEquals(attachmentJsonNode, outputAttachmentJsonNode);
    }

    private void mockSchemaServiceWithFields(List<UploadFieldDefinition> fieldDefList) {
        UploadSchema schema = UploadSchema.create();
        schema.setSchemaId(SCHEMA_ID);
        schema.setName(SCHEMA_NAME);
        schema.setRevision(SCHEMA_REV);
        schema.setFieldDefinitions(fieldDefList);

        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                schema);
    }

    // Makes a realistic info.json for the test.
    private static ObjectNode makeInfoJson() {
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_APP_VERSION, APP_VERSION);
        infoJsonNode.put(UploadUtil.FIELD_CREATED_ON, CREATED_ON_STRING);
        infoJsonNode.put(UploadUtil.FIELD_FORMAT, UploadFormat.V2_GENERIC.toString().toLowerCase());
        infoJsonNode.put(UploadUtil.FIELD_ITEM, SCHEMA_ID);
        infoJsonNode.put(UploadUtil.FIELD_PHONE_INFO, PHONE_INFO);
        infoJsonNode.put(UploadUtil.FIELD_SCHEMA_REV, SCHEMA_REV);
        return infoJsonNode;
    }

    private static UploadValidationContext makeContextWithContent(Map<String, JsonNode> jsonDataMap,
            Map<String, byte[]> unzippedDataMap) {
        UploadValidationContext context = new UploadValidationContext();

        // Put the content (JSON data map and unzipped data map) into the context.
        context.setJsonDataMap(jsonDataMap);
        context.setUnzippedDataMap(unzippedDataMap);

        // Handler expects the context to have these attributes, including the empty data map and empty attachments
        // map.
        context.setStudy(TestConstants.TEST_STUDY);
        context.setAttachmentsByFieldName(new HashMap<>());

        HealthDataRecord record = HealthDataRecord.create();
        record.setData(BridgeObjectMapper.get().createObjectNode());
        context.setHealthDataRecord(record);

        return context;
    }

    private static void validateCommonProps(UploadValidationContext context) {
        // Validate common health data record props.
        HealthDataRecord record = context.getHealthDataRecord();
        assertEquals(CREATED_ON_MILLIS, record.getCreatedOn().longValue());
        assertEquals(CREATED_ON_TIMEZONE, record.getCreatedOnTimeZone());
        assertEquals(SCHEMA_ID, record.getSchemaId());
        assertEquals(SCHEMA_REV, record.getSchemaRevision());

        // No messages.
        assertTrue(context.getMessageList().isEmpty());
    }
}
