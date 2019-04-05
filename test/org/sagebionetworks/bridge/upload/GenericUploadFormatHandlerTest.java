package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class GenericUploadFormatHandlerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String ATTACHMENT_ID = "dummy-attachment-id";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final String SCHEMA_NAME = "Test Schema";
    private static final int SCHEMA_REV = 3;
    private static final String UPLOAD_ID = "upload-id";

    private static final String CREATED_ON_STRING = "2017-09-26T01:10:21.173+0900";
    private static final long CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(CREATED_ON_STRING);
    private static final String CREATED_ON_TIMEZONE = "+0900";

    private InMemoryFileHelper inMemoryFileHelper;
    private GenericUploadFormatHandler handler;
    private UploadFileHelper mockUploadFileHelper;
    private UploadSchemaService mockSchemaService;
    private File tmpDir;

    @Before
    public void setup() throws Exception {
        // Set up InMemoryFileHelper with tmp dir
        inMemoryFileHelper = new InMemoryFileHelper();
        tmpDir = inMemoryFileHelper.createTempDir();

        // Mock dependencies
        mockUploadFileHelper = mock(UploadFileHelper.class);
        when(mockUploadFileHelper.findValueForField(any(), any(), any(), any())).thenReturn(TextNode.valueOf(
                ATTACHMENT_ID));

        mockSchemaService = mock(UploadSchemaService.class);

        // Set up handler
        handler = new GenericUploadFormatHandler();
        handler.setFileHelper(inMemoryFileHelper);
        handler.setUploadFileHelper(mockUploadFileHelper);
        handler.setUploadSchemaService(mockSchemaService);
    }

    @Test
    public void test() throws Exception {
        // Most of the complexities in this class have been moved to UploadFileHelper. As a result, we only need to
        // test 1 field and treat it as a passthrough to UploadFileHelper. The only behavior we need to test is
        // filename sanitization.

        // mock schema service
        UploadFieldDefinition sanitizeAttachmentTxtField = new UploadFieldDefinition.Builder()
                .withName("sanitize____attachment.txt").withType(UploadFieldType.ATTACHMENT_V2)
                .withFileExtension(".txt").withMimeType("text/plain").build();
        mockSchemaServiceWithFields(sanitizeAttachmentTxtField);

        // Setup inputs.
        File sanitizeAttachmentTxtFile = makeFileWithContent("sanitize!@#$attachment.txt",
                "Sanitize my filename");
        Map<String, File> fileMap = ImmutableMap.of("sanitize!@#$attachment.txt", sanitizeAttachmentTxtFile);

        UploadValidationContext context = makeContextWithContent(fileMap);
        context.setInfoJsonNode(makeInfoJson());

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(1, dataMap.size());
        assertEquals(ATTACHMENT_ID, dataMap.get("sanitize____attachment.txt").textValue());

        ArgumentCaptor<Map> sanitizedFileMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockUploadFileHelper).findValueForField(eq(UPLOAD_ID), sanitizedFileMapCaptor.capture(),
                eq(sanitizeAttachmentTxtField), any());

        Map<String, File> sanitizedFileMap = sanitizedFileMapCaptor.getValue();
        assertEquals(1, sanitizedFileMap.size());
        assertSame(sanitizeAttachmentTxtFile, sanitizedFileMap.get("sanitize____attachment.txt"));
    }

    @Test
    public void withDataFilename() throws Exception {
        // Same test as above, excecpt with the data filename parameter.

        // mock schema service
        UploadFieldDefinition fooField = new UploadFieldDefinition.Builder().withName("foo")
                .withType(UploadFieldType.STRING).withMaxLength(24).build();
        UploadFieldDefinition barField = new UploadFieldDefinition.Builder().withName("bar")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        UploadFieldDefinition sanitizeAttachmentTxtField = new UploadFieldDefinition.Builder()
                .withName("sanitize____attachment.txt").withType(UploadFieldType.ATTACHMENT_V2)
                .withFileExtension(".txt").withMimeType("text/plain").build();
        mockSchemaServiceWithFields(fooField, barField, sanitizeAttachmentTxtField);

        // Mock UploadFileHelper for the datafile-specific attachment.
        when(mockUploadFileHelper.uploadJsonNodeAsAttachment(any(), any(), any())).thenReturn(TextNode.valueOf(
                "data-file-attachment-id"));

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar is an attachment\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);

        File sanitizeAttachmentTxtFile = makeFileWithContent("sanitize!@#$attachment.txt",
                "Sanitize my filename");

        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("record.json", recordJsonFile)
                .put("sanitize!@#$attachment.txt", sanitizeAttachmentTxtFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(3, dataMap.size());
        assertEquals("foo-value", dataMap.get("foo").textValue());
        assertEquals("data-file-attachment-id", dataMap.get("bar").textValue());
        assertEquals(ATTACHMENT_ID, dataMap.get("sanitize____attachment.txt").textValue());

        // Verify calls to UploadFileHelper.
        verify(mockUploadFileHelper).uploadJsonNodeAsAttachment(TextNode.valueOf("bar is an attachment"), UPLOAD_ID,
                "bar");

        ArgumentCaptor<Map> sanitizedFileMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockUploadFileHelper).findValueForField(eq(UPLOAD_ID), sanitizedFileMapCaptor.capture(),
                eq(sanitizeAttachmentTxtField), any());

        Map<String, File> sanitizedFileMap = sanitizedFileMapCaptor.getValue();
        assertEquals(2, sanitizedFileMap.size());
        assertSame(recordJsonFile, sanitizedFileMap.get("record.json"));
        assertSame(sanitizeAttachmentTxtFile, sanitizedFileMap.get("sanitize____attachment.txt"));

        // We don't call mockUploadFileHelper for any other field.
        verifyNoMoreInteractions(mockUploadFileHelper);
    }

    @Test
    public void dataFileTooLarge() throws Exception {
        // Override file size limit to make this easier to test.
        handler.setDataFileSizeLimit(40);

        // mock schema service
        UploadFieldDefinition fooFieldDef = new UploadFieldDefinition.Builder().withName("foo")
                .withType(UploadFieldType.STRING).withMaxLength(24).build();
        UploadFieldDefinition barFieldDef = new UploadFieldDefinition.Builder().withName("bar")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        mockSchemaServiceWithFields(fooFieldDef, barFieldDef);

        // Upload file helper should just return null for this test.
        when(mockUploadFileHelper.findValueForField(any(), any(), any(), any())).thenReturn(null);

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar is an attachment\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);

        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("record.json", recordJsonFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        // Data map is empty.
        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(0, dataMap.size());

        // Since we skipped the data file (too large), we asked the file helper (which didn't find any results).
        verify(mockUploadFileHelper).findValueForField(eq(UPLOAD_ID), any(), eq(fooFieldDef), any());
        verify(mockUploadFileHelper).findValueForField(eq(UPLOAD_ID), any(), eq(barFieldDef), any());

        // We don't call mockUploadFileHelper for any other field.
        verifyNoMoreInteractions(mockUploadFileHelper);
    }

    @Test
    public void answersFieldFromDataFile() throws Exception {
        // Mock schema service.
        mockSchemaServiceWithFields(UploadUtil.ANSWERS_FIELD_DEF);

        // Mock dependencies.
        when(mockUploadFileHelper.findValueForField(any(), any(), any(), any())).thenReturn(null);
        when(mockUploadFileHelper.uploadJsonNodeAsAttachment(any(), any(), any())).thenReturn(TextNode.valueOf(
                "answers-attachment-id"));

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("record.json", recordJsonFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(1, dataMap.size());
        assertEquals("answers-attachment-id", dataMap.get(UploadUtil.FIELD_ANSWERS).textValue());

        // Verify answers attachment.
        ArgumentCaptor<JsonNode> answersNodeCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(mockUploadFileHelper).uploadJsonNodeAsAttachment(answersNodeCaptor.capture(), eq(UPLOAD_ID),
                eq(UploadUtil.FIELD_ANSWERS));

        JsonNode answersNode = answersNodeCaptor.getValue();
        assertEquals(2, answersNode.size());
        assertEquals("foo-value", answersNode.get("foo").textValue());
        assertEquals("bar-value", answersNode.get("bar").textValue());
    }

    @Test
    public void answersFieldAsString() throws Exception {
        // Mock schema service.
        UploadFieldDefinition answersStringFieldDef = new UploadFieldDefinition.Builder()
                .withName(UploadUtil.FIELD_ANSWERS).withType(UploadFieldType.STRING).withUnboundedText(true).build();
        mockSchemaServiceWithFields(answersStringFieldDef);

        // Mock dependencies.
        when(mockUploadFileHelper.findValueForField(any(), any(), any(), any())).thenReturn(null);

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("record.json", recordJsonFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(1, dataMap.size());

        JsonNode answersNode = dataMap.get(UploadUtil.FIELD_ANSWERS);
        assertEquals(2, answersNode.size());
        assertEquals("foo-value", answersNode.get("foo").textValue());
        assertEquals("bar-value", answersNode.get("bar").textValue());

        // We don't upload anything.
        verify(mockUploadFileHelper, never()).uploadJsonNodeAsAttachment(any(), any(), any());
    }

    @Test
    public void answersFieldNullDataFile() throws Exception {
        // Mock schema service.
        mockSchemaServiceWithFields(UploadUtil.ANSWERS_FIELD_DEF);

        // Mock dependencies.
        when(mockUploadFileHelper.findValueForField(any(), any(), any(), any())).thenReturn(null);

        // Setup inputs.
        UploadValidationContext context = makeContextWithContent(ImmutableMap.of());
        ObjectNode infoJsonNode = makeInfoJson();
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(0, dataMap.size());

        // We don't upload anything.
        verify(mockUploadFileHelper, never()).uploadJsonNodeAsAttachment(any(), any(), any());
    }

    @Test
    public void answersOverriddenInDataFile() throws Exception {
        // Mock schema service.
        mockSchemaServiceWithFields(UploadUtil.ANSWERS_FIELD_DEF);

        // Mock dependencies.
        when(mockUploadFileHelper.uploadJsonNodeAsAttachment(any(), any(), any())).thenReturn(TextNode.valueOf(
                "answers-attachment-id"));

        // Setup inputs.
        String recordJsonText = "{\n" +
                "   \"" + UploadUtil.FIELD_ANSWERS + "\":{" +
                "       \"foo\":\"overriden foo\",\n" +
                "       \"bar\":\"overriden bar\"\n" +
                "   },\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("record.json", recordJsonFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(1, dataMap.size());
        assertEquals("answers-attachment-id", dataMap.get(UploadUtil.FIELD_ANSWERS).textValue());

        // Verify answers attachment.
        ArgumentCaptor<JsonNode> answersNodeCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(mockUploadFileHelper).uploadJsonNodeAsAttachment(answersNodeCaptor.capture(), eq(UPLOAD_ID),
                eq(UploadUtil.FIELD_ANSWERS));

        JsonNode answersNode = answersNodeCaptor.getValue();
        assertEquals(2, answersNode.size());
        assertEquals("overriden foo", answersNode.get("foo").textValue());
        assertEquals("overriden bar", answersNode.get("bar").textValue());
    }

    @Test
    public void answersOverriddenInOtherFile() throws Exception {
        // Mock schema service.
        mockSchemaServiceWithFields(UploadUtil.ANSWERS_FIELD_DEF);

        // Setup inputs.
        String answersJsonText = "{\n" +
                "   \"foo\":\"overridden foo\",\n" +
                "   \"bar\":\"overridden bar\"\n" +
                "}";
        File answersFile = makeFileWithContent(UploadUtil.FIELD_ANSWERS, answersJsonText);

        String recordJsonText = "{\n" +
                "   \"foo\":\"foo-value\",\n" +
                "   \"bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent("record.json", recordJsonText);
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put(UploadUtil.FIELD_ANSWERS, answersFile)
                .put("record.json", recordJsonFile).build();

        UploadValidationContext context = makeContextWithContent(fileMap);
        ObjectNode infoJsonNode = makeInfoJson();
        infoJsonNode.put(UploadUtil.FIELD_DATA_FILENAME, "record.json");
        context.setInfoJsonNode(infoJsonNode);

        // execute and validate
        handler.handle(context);
        validateCommonProps(context);

        JsonNode dataMap = context.getHealthDataRecord().getData();
        assertEquals(1, dataMap.size());
        assertEquals(ATTACHMENT_ID, dataMap.get(UploadUtil.FIELD_ANSWERS).textValue());

        // Verify call to findValueForField. This passes in both "answers" and "record.json".
        ArgumentCaptor<Map> sanitizedFileMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockUploadFileHelper).findValueForField(eq(UPLOAD_ID), sanitizedFileMapCaptor.capture(),
                eq(UploadUtil.ANSWERS_FIELD_DEF), any());

        Map<String, File> sanitizedFileMap = sanitizedFileMapCaptor.getValue();
        assertEquals(2, sanitizedFileMap.size());
        assertSame(answersFile, sanitizedFileMap.get(UploadUtil.FIELD_ANSWERS));
        assertSame(recordJsonFile, sanitizedFileMap.get("record.json"));

        // We don't upload anything.
        verify(mockUploadFileHelper, never()).uploadJsonNodeAsAttachment(any(), any(), any());
    }

    private void mockSchemaServiceWithFields(UploadFieldDefinition... fieldDefVarargs) {
        UploadSchema schema = UploadSchema.create();
        schema.setSchemaId(SCHEMA_ID);
        schema.setName(SCHEMA_NAME);
        schema.setRevision(SCHEMA_REV);
        schema.setFieldDefinitions(ImmutableList.copyOf(fieldDefVarargs));

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

    private static UploadValidationContext makeContextWithContent(Map<String, File> fileMap) {
        UploadValidationContext context = new UploadValidationContext();

        // Make dummy upload with upload ID.
        Upload upload = Upload.create();
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);

        // Put the file map into the context.
        context.setUnzippedDataFileMap(fileMap);

        // Handler expects the context to have these attributes, including the empty data map.
        context.setStudy(TestConstants.TEST_STUDY);

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

    private File makeFileWithContent(String name, String content) {
        File file = inMemoryFileHelper.newFile(tmpDir, name);
        inMemoryFileHelper.writeBytes(file, content.getBytes(Charsets.UTF_8));
        return file;
    }
}
