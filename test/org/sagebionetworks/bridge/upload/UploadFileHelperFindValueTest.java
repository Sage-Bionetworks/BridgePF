package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadFileHelperFindValueTest {
    private static final String FIELD_NAME_FILE = "record.json";
    private static final String FIELD_NAME_JSON_KEY = "record.json.foo";
    private static final String UPLOAD_ID = "upload-id";

    private InMemoryFileHelper inMemoryFileHelper;
    private S3Helper mockS3Helper;
    private File tmpDir;
    private UploadFileHelper uploadFileHelper;
    private ArgumentCaptor<ObjectMetadata> metadataCaptor;

    @Before
    public void before() {
        // Spy file helper, so we can check to see how many times we read the disk later. Also make a dummy temp dir,
        // as an in-memory place we can put files into.
        inMemoryFileHelper = spy(new InMemoryFileHelper());
        tmpDir = inMemoryFileHelper.createTempDir();

        // Mock dependencies.
        mockS3Helper = mock(S3Helper.class);

        metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        
        // Create UploadFileHelper.
        uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setFileHelper(inMemoryFileHelper);
        uploadFileHelper.setS3Helper(mockS3Helper);
    }

    @Test
    public void attachmentFile() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "dummy content");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        String expectedAttachmentFilename = UPLOAD_ID + '-' + FIELD_NAME_FILE;
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(expectedAttachmentFilename, result.textValue());

        // Verify uploaded file
        verify(mockS3Helper).writeFileToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(expectedAttachmentFilename),
                eq(recordJsonFile), metadataCaptor.capture());
        
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
    }

    @Test
    public void attachmentFileEmpty() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map. File should exist but have empty content.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void inlineFile() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "\"dummy content\"");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals("dummy content", result.textValue());

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void inlineFileTooLarge() throws Exception {
        // Set file size limit to something very small, to hit our test for sure.
        uploadFileHelper.setInlineFileSizeLimit(10);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "\"This file content is definitely exceeds our file size limit.\"");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueNoValueFound() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map. One file doesn't match the name, and the other file matches the name but doesn't have the key.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"bar\":\"bar-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueAttachment() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map. For good measure, have one file that doesn't match.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"foo\":\"record-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        String expectedAttachmentFilename = UPLOAD_ID + '-' + FIELD_NAME_JSON_KEY;
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(expectedAttachmentFilename, result.textValue());

        // Verify uploaded file
        verify(mockS3Helper).writeBytesToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(expectedAttachmentFilename),
                eq("\"record-value\"".getBytes(Charsets.UTF_8)), metadataCaptor.capture());
        
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
    }

    @Test
    public void findValueInline() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map. For good measure, have one file that doesn't match.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"foo\":\"record-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals("record-value", result.textValue());

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueInlineWarningLimit() throws Exception {
        // Set limits to something that's easier to test.
        uploadFileHelper.setParsedJsonWarningLimit(20);
        uploadFileHelper.setParsedJsonFileSizeLimit(40);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "{\"foo\":\"Long but not too long\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute - The file is too large. Skip.
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals("Long but not too long", result.textValue());

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueInlineFileSizeLimit() throws Exception {
        // Set limits to something that's easier to test.
        uploadFileHelper.setParsedJsonWarningLimit(20);
        uploadFileHelper.setParsedJsonFileSizeLimit(40);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "{\"foo\":\"This is the value, but the file is definitely too long to parse\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute - The file is long enough to warn, but not long enough to skip.
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void multipleKeys() throws Exception {
        // Make field defs.
        UploadFieldDefinition fooFieldDef = new UploadFieldDefinition.Builder().withName("record.json.sanitize____foo")
                .withType(UploadFieldType.STRING).build();
        UploadFieldDefinition barFieldDef = new UploadFieldDefinition.Builder().withName("record.json.sanitize____bar")
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        String recordJsonText = "{\n" +
                "   \"sanitize!@#$foo\":\"foo-value\",\n" +
                "   \"sanitize!@#$bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, recordJsonText);
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        Map<String, Map<String, JsonNode>> cache = new HashMap<>();

        JsonNode fooResult = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fooFieldDef, cache);
        assertEquals("foo-value", fooResult.textValue());

        JsonNode barResult = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, barFieldDef, cache);
        assertEquals("bar-value", barResult.textValue());

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);

        // Verify we only read the file once.
        verify(inMemoryFileHelper, times(1)).getInputStream(recordJsonFile);
    }

    private File makeFileWithContent(String name, String content) {
        File file = inMemoryFileHelper.newFile(tmpDir, name);
        inMemoryFileHelper.writeBytes(file, content.getBytes(Charsets.UTF_8));
        return file;
    }
}
