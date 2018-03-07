package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public class InitRecordHandlerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String HEALTH_CODE = "test-health-code";
    private static final LocalDate MOCK_NOW_DATE = LocalDate.parse("2017-09-26");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-09-26T18:04:13.855-0700").getMillis();
    private static final String UPLOAD_ID = "test-upload";

    private InitRecordHandler handler;
    private InMemoryFileHelper inMemoryFileHelper;
    private File tmpDir;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void before() {
        // Init fileHelper and tmpDir
        inMemoryFileHelper = new InMemoryFileHelper();
        tmpDir = inMemoryFileHelper.createTempDir();

        // Init handler
        handler = new InitRecordHandler();
        handler.setFileHelper(inMemoryFileHelper);
    }

    private UploadValidationContext setupContextWithJsonDataMap(Map<String, JsonNode> jsonDataMap) {
        UploadValidationContext context = new UploadValidationContext();
        //noinspection ConstantConditions
        context.setUnzippedDataFileMap(Maps.transformEntries(jsonDataMap,
                (name, node) -> makeFileWithContent(name, node.toString())));

        // Contexts always include studyId.
        context.setStudy(TestConstants.TEST_STUDY);

        // And upload (with upload ID and health code).
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode(HEALTH_CODE);
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);

        return context;
    }

    @Test
    public void normalCase() throws Exception {
        // Setup context with info.json.
        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>builder()
                .put(UploadUtil.FILENAME_INFO_JSON, makeInfoJson()).build();
        UploadValidationContext context = setupContextWithJsonDataMap(jsonDataMap);

        // execute and validate
        handler.handle(context);
        validateCommonContextAttributes(context);

        // User metadata is null
        assertNull(context.getHealthDataRecord().getUserMetadata());

        // No messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    @Test
    public void noInfoJson() {
        // Setup context with no files (info.json is the only one that matters).
        UploadValidationContext context = setupContextWithJsonDataMap(ImmutableMap.of());

        // execute and catch exception
        try {
            handler.handle(context);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            assertEquals("upload ID " + UPLOAD_ID + " does not contain info.json file", ex.getMessage());
        }
    }

    @Test
    public void withMetadata() throws Exception {
        // Setup metadata.
        ObjectNode metadataJsonNode = BridgeObjectMapper.get().createObjectNode();
        metadataJsonNode.put("my-meta-key", "my-meta-value");

        // Setup context with info.json and metadata.json.
        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>builder()
                .put(UploadUtil.FILENAME_INFO_JSON, makeInfoJson())
                .put(UploadUtil.FILENAME_METADATA_JSON, metadataJsonNode).build();
        UploadValidationContext context = setupContextWithJsonDataMap(jsonDataMap);

        // execute and validate
        handler.handle(context);
        validateCommonContextAttributes(context);

        // user metadata should match.
        assertEquals(metadataJsonNode, context.getHealthDataRecord().getUserMetadata());

        // No messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    private static void validateCommonContextAttributes(UploadValidationContext context) {
        // Validate health data record props.
        HealthDataRecord record = context.getHealthDataRecord();
        assertEquals(APP_VERSION, record.getAppVersion());
        assertEquals(HEALTH_CODE, record.getHealthCode());
        assertEquals(PHONE_INFO, record.getPhoneInfo());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, record.getStudyId());
        assertEquals(MOCK_NOW_DATE, record.getUploadDate());
        assertEquals(UPLOAD_ID, record.getUploadId());
        assertEquals(MOCK_NOW_MILLIS, record.getUploadedOn().longValue());

        // Record contains an empty object node.
        assertTrue(record.getData().isObject());
        assertEquals(0, record.getData().size());

        // Don't validate inside metadata. If it exists, that's all that matters.
        assertNotNull(record.getMetadata());
    }

    private static JsonNode makeInfoJson() {
        // info.json has more fields than this, but the only fields this handler cares about are appVersion and
        // phoneInfo.
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_APP_VERSION, APP_VERSION);
        infoJsonNode.put(UploadUtil.FIELD_PHONE_INFO, PHONE_INFO);
        return infoJsonNode;
    }

    @Test
    public void parseJsonNotInMap() {
        JsonNode result = handler.parseFileAsJson(ImmutableMap.of(), UploadUtil.FILENAME_INFO_JSON);
        assertNull(result);
    }

    @Test
    public void parseJsonNotExists() {
        // If we don't write to the file, it doesn't exist.
        File file = inMemoryFileHelper.newFile(tmpDir, UploadUtil.FILENAME_INFO_JSON);
        Map<String, File> fileMap = ImmutableMap.of(UploadUtil.FILENAME_INFO_JSON, file);

        JsonNode result = handler.parseFileAsJson(fileMap, UploadUtil.FILENAME_INFO_JSON);
        assertNull(result);
    }

    @Test
    public void parseJsonInvalidJson() {
        Map<String, File> fileMap = makeInfoJsonFileMapWithContent("invalid JSON");
        JsonNode result = handler.parseFileAsJson(fileMap, UploadUtil.FILENAME_INFO_JSON);
        assertNull(result);
    }

    @Test
    public void parseJsonJsonNull() {
        Map<String, File> fileMap = makeInfoJsonFileMapWithContent("null");
        JsonNode result = handler.parseFileAsJson(fileMap, UploadUtil.FILENAME_INFO_JSON);
        assertNull(result);
    }

    @Test
    public void parseJsonWrongType() {
        Map<String, File> fileMap = makeInfoJsonFileMapWithContent("\"not an object\"");
        JsonNode result = handler.parseFileAsJson(fileMap, UploadUtil.FILENAME_INFO_JSON);
        assertNull(result);
    }

    @Test
    public void parseJsonSuccess() {
        Map<String, File> fileMap = makeInfoJsonFileMapWithContent("{\"my-key\":\"my-value\"}");
        JsonNode result = handler.parseFileAsJson(fileMap, UploadUtil.FILENAME_INFO_JSON);
        assertEquals(1, result.size());
        assertEquals("my-value", result.get("my-key").textValue());
    }

    private Map<String, File> makeInfoJsonFileMapWithContent(String content) {
        File file = makeFileWithContent(UploadUtil.FILENAME_INFO_JSON, content);
        return ImmutableMap.of(UploadUtil.FILENAME_INFO_JSON, file);
    }

    private File makeFileWithContent(String name, String content) {
        File file = inMemoryFileHelper.newFile(tmpDir, name);
        inMemoryFileHelper.writeBytes(file, content.getBytes(Charsets.UTF_8));
        return file;
    }
}
