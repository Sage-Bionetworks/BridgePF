package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

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
        context.setStudy(TEST_STUDY);

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
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "test-schema", 1)).thenReturn(
                testSchema);
        handler.setUploadSchemaService(mockSchemaService);

        // mock study service - this is to get the shouldThrow (strictUploadValidationEnabled) flag
        DynamoStudy testStudy = new DynamoStudy();
        testStudy.setStrictUploadValidationEnabled(shouldThrow);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(testStudy);
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
                new DynamoUploadFieldDefinition.Builder().withName("multi-choice")
                        .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo", "bar", "baz").build(),
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
                "   \"multi-choice\":[\"foo\", \"bar\", \"baz\"],\n" +
                "   \"string timestamp\":\"2015-07-24T18:49:54-07:00\",\n" +
                "   \"long timestamp\":1437787098066,\n" +
                "   \"present optional json\":\"optional, but present\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // execute and validate
        test(additionalFieldDefList, additionalAttachmentsMap, additionalJsonNode, null, false);
    }

    @Test
    public void canonicalizedValue() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("canonicalized int").withType(UploadFieldType.INT)
                        .build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"canonicalized int\":\"23\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, null, false);

        // verify canonicalized value
        JsonNode dataNode = context.getHealthDataRecordBuilder().getData();
        JsonNode intNode = dataNode.get("canonicalized int");
        assertTrue(intNode.isIntegralNumber());
        assertEquals(23, intNode.intValue());
    }

    @Test
    public void invalidValue() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid int").withType(UploadFieldType.INT)
                        .build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid int\":\"ninety-nine\"\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("invalid int");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
    }

    @Test
    public void invalidMultiChoice() throws Exception {
        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("invalid multi-choice")
                        .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("good1", "good2").build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"invalid multi-choice\":[\"bad1\", \"good2\", \"bad2\"]\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of(
                "Multi-Choice field invalid multi-choice contains invalid answer bad1",
                "Multi-Choice field invalid multi-choice contains invalid answer bad2");

        // execute and validate
        test(additionalFieldDefList, null, additionalJsonNode, expectedErrorList, true);
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
                new DynamoUploadFieldDefinition.Builder().withName("optional int")
                        .withType(UploadFieldType.INT).withRequired(false).build());

        // additional JSON data
        String additionalJsonText = "{\n" +
                "   \"optional int\":false\n" +
                "}";
        JsonNode additionalJsonNode = BridgeObjectMapper.get().readTree(additionalJsonText);

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("optional int");

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
    public void appVersionBelowMax() throws Exception {
        // Upload is missing a field that's required and has a maxAppVersion. Upload has a version below the
        // maxAppVersion. This gets validated and throws.

        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("required with max app version").withType(UploadFieldType.STRING).withMaxAppVersion(20)
                .build());

        // expected errors
        List<String> expectedErrorList = ImmutableList.of("required with max app version");

        // add app version to context
        context.setAppVersion(10);

        // execute and validate
        test(additionalFieldDefList, null, null, expectedErrorList, true);
    }

    @Test
    public void appVersionAboveMax() throws Exception {
        // Upload is missing a field that's required and has a maxAppVersion. Upload has a version above the
        // maxAppVersion. The missing field is ignored.

        // additional field defs
        List<UploadFieldDefinition> additionalFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("required with max app version").withType(UploadFieldType.STRING).withMaxAppVersion(20)
                .build());

        // add app version to context
        context.setAppVersion(30);

        // execute and validate
        test(additionalFieldDefList, null, null, null, false);
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
