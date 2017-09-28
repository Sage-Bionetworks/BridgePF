package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public class InitRecordHandlerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String HEALTH_CODE = "test-health-code";
    private static final LocalDate MOCK_NOW_DATE = LocalDate.parse("2017-09-26");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-09-26T18:04:13.855-0700").getMillis();
    private static final String UPLOAD_ID = "test-upload";

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    private static UploadValidationContext setupContextWithJsonDataMap(Map<String, JsonNode> jsonDataMap) {
        UploadValidationContext context = new UploadValidationContext();
        context.setJsonDataMap(jsonDataMap);

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
        InitRecordHandler handler = new InitRecordHandler();
        handler.handle(context);
        validateCommonContextAttributes(context);

        // No messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    @Test
    public void noInfoJson() {
        // Setup context with no files (info.json is the only one that matters).
        UploadValidationContext context = setupContextWithJsonDataMap(ImmutableMap.of());

        // execute and catch exception
        InitRecordHandler handler = new InitRecordHandler();
        try {
            handler.handle(context);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            assertEquals("upload ID " + UPLOAD_ID + " does not contain info.json file", ex.getMessage());
        }
    }

    @Test
    public void metadataJsonIsNotAnObject() throws Exception {
        // Setup context with info.json. metadata.json is some string.
        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>builder()
                .put(UploadUtil.FILENAME_INFO_JSON, makeInfoJson())
                .put(UploadUtil.FILENAME_METADATA_JSON, TextNode.valueOf("doesn't matter")).build();
        UploadValidationContext context = setupContextWithJsonDataMap(jsonDataMap);

        // execute and validate
        InitRecordHandler handler = new InitRecordHandler();
        handler.handle(context);
        validateCommonContextAttributes(context);

        // No metadata.
        assertNull(context.getHealthDataRecord().getUserMetadata());

        // And there's a message.
        assertEquals(1, context.getMessageList().size());
        assertEquals("upload " + UPLOAD_ID + " contains metadata.json, but it is not a JSON object",
                context.getMessageList().get(0));
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
        InitRecordHandler handler = new InitRecordHandler();
        handler.handle(context);
        validateCommonContextAttributes(context);

        // user metadata should match.
        assertEquals(metadataJsonNode, context.getHealthDataRecord().getUserMetadata());

        // No messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    private static void validateCommonContextAttributes(UploadValidationContext context) {
        // We have a non-null but empty attachment map.
        assertTrue(context.getAttachmentsByFieldName().isEmpty());

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
}
