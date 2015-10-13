package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class StrictValidationHandlerTest {
    private final static byte[] DUMMY_ATTACHMENT = new byte[0];

    private UploadValidationContext context;
    private StrictValidationHandler handler;

    @Before
    public void setup() {
        handler = new StrictValidationHandler();

        // Set up common context attributes.
        context = new UploadValidationContext();
        context.setStudy(TestConstants.TEST_STUDY);

        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId("test-upload");
        context.setUpload(upload);
    }

    private void test(List<UploadFieldDefinition> additionalFieldDefList, Map<String, byte[]> additionalAttachmentMap,
            JsonNode additionalJsonNode, List<String> expectedErrorList, boolean shouldThrow) throws Exception {
        // Basic schema with a basic attachment, basic field, and additional fields.
        DynamoUploadSchema testSchema = new DynamoUploadSchema();
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("attachment blob")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("string")
                .withType(UploadFieldType.STRING).build());
        if (additionalFieldDefList != null) {
            fieldDefList.addAll(additionalFieldDefList);
        }
        testSchema.setFieldDefinitions(fieldDefList);

        // mock schema service
        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-schema", 1)).thenReturn(
                testSchema);
        handler.setUploadSchemaService(mockSchemaService);

        // mock study service - this is to get the shouldThrow (strictUploadValidationEnabled) flag
        DynamoStudy testStudy = new DynamoStudy();
        testStudy.setStrictUploadValidationEnabled(shouldThrow);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(testStudy);
        handler.setStudyService(mockStudyService);

        // set up attachments map
        Map<String, byte[]> attachmentsMap = new HashMap<>();
        attachmentsMap.put("attachment blob", DUMMY_ATTACHMENT);
        if (additionalAttachmentMap != null) {
            attachmentsMap.putAll(additionalAttachmentMap);
        }
        context.setAttachmentsByFieldName(attachmentsMap);

        // set up JSON data
        String jsonDataString = "{\n" +
                "   \"string\":\"This is a string\"\n" +
                "}";
        ObjectNode jsonDataNode = (ObjectNode) BridgeObjectMapper.get().readTree(jsonDataString);
        if (additionalJsonNode != null) {
            ObjectNode additionalObjectNode = (ObjectNode) additionalJsonNode;
            Iterator<Map.Entry<String, JsonNode>> additionalJsonIter = additionalObjectNode.fields();
            while (additionalJsonIter.hasNext()) {
                Map.Entry<String, JsonNode> oneAdditionalJson = additionalJsonIter.next();
                jsonDataNode.set(oneAdditionalJson.getKey(), oneAdditionalJson.getValue());
            }
        }

        // write JSON data to health data record builder
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder().withData(jsonDataNode)
                .withSchemaId("test-schema").withSchemaRevision(1);
        context.setHealthDataRecordBuilder(recordBuilder);

        if (shouldThrow) {
            // execute - Catch the exception and make sure the exception message contains our expected error messages.
            Exception thrownEx = null;
            try {
                handler.handle(context);
                fail("Expected exception");
            } catch (UploadValidationException ex) {
                thrownEx = ex;
            }
            assertFalse(context.getMessageList().isEmpty());
            for (String oneExpectedError : expectedErrorList) {
                assertTrue("Expected error: " + oneExpectedError, thrownEx.getMessage().contains(oneExpectedError));
            }
        } else {
            handler.handle(context);

            if (expectedErrorList == null || expectedErrorList.isEmpty()) {
                assertTrue(context.getMessageList().isEmpty());
            } else {
                // We don't want to do string matching. Instead, the quickest way to verify this is to concatenate the
                // context message list together and make sure our expected strings are in there.
                String concatMessage = Joiner.on('\n').join(context.getMessageList());
                for (String oneExpectedError : expectedErrorList) {
                    assertTrue("Expected error: " + oneExpectedError, concatMessage.contains(oneExpectedError));
                }
            }
        }
    }

    @Test
    public void happyCase() throws Exception {
        // additional field defs
        // Test one of each type.
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("attachment csv")
                        .withType(UploadFieldType.ATTACHMENT_CSV).build(),
                new DynamoUploadFieldDefinition.Builder().withName("attachment json blob")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("attachment json table")
                        .withType(UploadFieldType.ATTACHMENT_JSON_TABLE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("boolean")
                        .withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("calendar date")
                        .withType(UploadFieldType.CALENDAR_DATE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("float")
                        .withType(UploadFieldType.FLOAT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("float with int value")
                        .withType(UploadFieldType.FLOAT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("inline json blob")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("int")
                        .withType(UploadFieldType.INT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("int with float value")
                        .withType(UploadFieldType.INT).build(),
                new DynamoUploadFieldDefinition.Builder().withName("string timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new DynamoUploadFieldDefinition.Builder().withName("long timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new DynamoUploadFieldDefinition.Builder().withName("missing optional attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("present optional attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("missing optional json")
                        .withType(UploadFieldType.STRING).withRequired(false).build(),
                new DynamoUploadFieldDefinition.Builder().withName("present optional json")
                        .withType(UploadFieldType.STRING).withRequired(false).build());

        // additional attachments map
        Map<String, byte[]> additionalAttachmentsMap = ImmutableMap.of(
                "attachment csv", DUMMY_ATTACHMENT,
                "attachment json blob", DUMMY_ATTACHMENT,
                "attachment json table", DUMMY_ATTACHMENT,
                "present optional attachment", DUMMY_ATTACHMENT);

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"boolean\":true,\n" +
                "   \"calendar date\":\"2015-07-24\",\n" +
                "   \"float\":3.14,\n" +
                "   \"float with int value\":13,\n" +
                "   \"inline json blob\":[\"inline\", \"json\", \"blob\"],\n" +
                "   \"int\":42,\n" +
                "   \"int with float value\":2.78,\n" +
                "   \"string timestamp\":\"2015-07-24T18:49:54-07:00\",\n" +
                "   \"long timestamp\":1437787098066,\n" +
                "   \"present optional json\":\"optional, but present\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // execute and validate
        test(additionalFieldDefList, additionalAttachmentsMap, additionalJsonNode, null, false);
    }

    @Test
    public void missingRequiredAttachment() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("missing required attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).build());

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("missing required attachment");

        // execute and validate
        test(additionalFieldDefList, null, null, expectedErrorList, true);
    }

    @Test
    public void missingRequiredField() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("missing required field")
                        .withType(UploadFieldType.STRING).build());

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("missing required field");

        // execute and validate
        test(additionalFieldDefList, null, null, expectedErrorList, true);
    }

    @Test
    public void optionalFieldStillGetsValidated() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("optional string")
                        .withType(UploadFieldType.STRING).withRequired(false).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"optional string\":false\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("optional string");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void jsonNullRequiredField() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("null required field")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"null required field\":null\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("null required field");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void invalidBoolean() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid boolean")
                        .withType(UploadFieldType.BOOLEAN).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid boolean\":1\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("invalid boolean");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void nonStringCalendarDate() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("non-string calendar date")
                        .withType(UploadFieldType.CALENDAR_DATE).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"non-string calendar date\":20150826\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("non-string calendar date");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void malformattedCalendarDate() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("malformatted calendar date")
                        .withType(UploadFieldType.CALENDAR_DATE).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"malformatted\":\"August 26, 2015\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("malformatted calendar date");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void invalidFloat() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid float")
                        .withType(UploadFieldType.FLOAT).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid float\":\"3.14\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("invalid float");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void invalidInt() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid int")
                        .withType(UploadFieldType.INT).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid int\":\"1337\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("invalid int");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void invalidString() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid string")
                        .withType(UploadFieldType.STRING).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid string\":[\"SingleChoice answer\"]\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("invalid string");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void malformattedTimestamp() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("malformatted timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"malformatted timestamp\":\"today 3pm\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("malformatted timestamp");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void wrongTypeTimestamp() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("wrong type timestamp")
                        .withType(UploadFieldType.TIMESTAMP).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"wrong type timestamp\":true\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("wrong type timestamp");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void multipleValidationErrors() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("missing required attachment")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("invalid int")
                        .withType(UploadFieldType.INT).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid int\":\"Math.PI\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("missing required attachment", "invalid int");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void studyConfiguredToNotThrow() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("missing required field")
                        .withType(UploadFieldType.STRING).build());

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("missing required field");

        // execute and validate
        test(additionalFieldDefList, null, null, expectedErrorList, false);
    }
}
