package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class IosSchemaValidationHandlerTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_STUDY_ID = "test-study";
    private static final String TEST_UPLOAD_DATE_STRING = "2015-04-13";
    private static final String TEST_UPLOAD_ID = "test-upload";

    private UploadValidationContext context;
    private IosSchemaValidationHandler handler;

    @Before
    public void setup() {
        // set up common params for test context
        // dummy study, all we need is the ID
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_ID);

        // For upload, we need uploadId, healthCode, and uploadDate
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setHealthCode(TEST_HEALTHCODE);
        upload.setUploadDate(LocalDate.parse(TEST_UPLOAD_DATE_STRING));

        context = new UploadValidationContext();
        context.setStudy(study);
        context.setUpload(upload);

        // set up test schemas
        // iOS Survey Schema is just a marker schema. It's not used for anything other than study ID, schema ID, and
        // rev, so we don't need to fill out the fields.
        DynamoUploadSchema iosSurveySchema = new DynamoUploadSchema();
        iosSurveySchema.setStudyId("test-study");
        iosSurveySchema.setSchemaId("ios-survey");
        iosSurveySchema.setRevision(1);
        iosSurveySchema.setName("iOS Survey");
        iosSurveySchema.setFieldDefinitions(ImmutableList.<UploadFieldDefinition>of());

        DynamoUploadSchema jsonDataSchema = new DynamoUploadSchema();
        jsonDataSchema.setStudyId("test-study");
        jsonDataSchema.setSchemaId("json-data");
        jsonDataSchema.setRevision(1);
        jsonDataSchema.setName("json-data");
        jsonDataSchema.setFieldDefinitions(ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("string.json.string")
                        .withType(UploadFieldType.STRING).build(),
                new DynamoUploadFieldDefinition.Builder().withName("blob.json.blob")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build()));

        DynamoUploadSchema nonJsonDataSchema = new DynamoUploadSchema();
        nonJsonDataSchema.setStudyId("test-study");
        nonJsonDataSchema.setSchemaId("non-json-data");
        nonJsonDataSchema.setRevision(1);
        nonJsonDataSchema.setName("non-json-data");
        nonJsonDataSchema.setFieldDefinitions(ImmutableList.<UploadFieldDefinition>of(
                new DynamoUploadFieldDefinition.Builder().withName("nonJsonFile.txt")
                        .withType(UploadFieldType.ATTACHMENT_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("jsonFile.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build()));

        // mock upload schema service
        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemasForStudy(study)).thenReturn(ImmutableList.<UploadSchema>of(
                iosSurveySchema, jsonDataSchema, nonJsonDataSchema));
        when(mockSchemaService.getUploadSchema(study, "ios-survey")).thenReturn(
                iosSurveySchema);

        // set up handler
        handler = new IosSchemaValidationHandler();
        handler.setUploadSchemaService(mockSchemaService);

        // health data dao is only used for getBuilder(), so we can just create one without any depedencies
        handler.setHealthDataDao(new DynamoHealthDataDao());
    }

    @Test
    public void survey() throws Exception {
        // fill in context with survey data
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"foo.json\",\n" +
                "       \"timestamp\":\"2015-04-02T03:26:59-07:00\"\n" +
                "   },{\n" +
                "       \"filename\":\"bar.json\",\n" +
                "       \"timestamp\":\"2015-04-02T03:27:09-07:00\"\n" +
                "   }],\n" +
                "   \"item\":\"test-survey\",\n" +
                "   \"taskRun\":\"test-taskRunId\"\n" +
                "}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        String fooAnswerJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"textAnswer\":\"foo answer\",\n" +
                "   \"startDate\":\"2015-04-02T03:26:57-07:00\",\n" +
                "   \"questionTypeName\":\"Text\",\n" +
                "   \"item\":\"foo\",\n" +
                "   \"endDate\":\"2015-04-02T03:26:59-07:00\"\n" +
                "}";
        JsonNode fooAnswerJsonNode = BridgeObjectMapper.get().readTree(fooAnswerJsonText);

        String barAnswerJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"numericAnswer\":\"bar answer\",\n" +
                "   \"startDate\":\"2015-04-02T03:27:05-07:00\",\n" +
                "   \"questionTypeName\":\"Integer\",\n" +
                "   \"item\":\"bar\",\n" +
                "   \"endDate\":\"2015-04-02T03:27:09-07:00\"\n" +
                "}";
        JsonNode barAnswerJsonNode = BridgeObjectMapper.get().readTree(barAnswerJsonText);

        context.setJsonDataMap(ImmutableMap.of(
                "info.json", infoJsonNode,
                "foo.json", fooAnswerJsonNode,
                "bar.json", barAnswerJsonNode));
        context.setUnzippedDataMap(ImmutableMap.<String, byte[]>of());

        // execute
        handler.handle(context);

        // validate
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        validateCommonRecordProps(recordBuilder);
        assertEquals(DateTime.parse("2015-04-02T03:27:09-07:00").getMillis(),
                recordBuilder.getCreatedOn().longValue());
        assertEquals("ios-survey", recordBuilder.getSchemaId());
        assertEquals(1, recordBuilder.getSchemaRevision());

        JsonNode dataNode = recordBuilder.getData();
        assertEquals(2, dataNode.size());
        assertEquals("test-survey", dataNode.get("item").textValue());
        assertEquals("test-taskRunId", dataNode.get("taskRunId").textValue());

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(1, attachmentMap.size());

        // Don't parse too deeply into the answers array. Just collect a set of question names (items) and make sure
        // we have them all.
        JsonNode answerArrayNode = BridgeObjectMapper.get().readTree(attachmentMap.get("answers"));
        Set<String> questionNameSet = new HashSet<>();
        int numAnswers = answerArrayNode.size();
        for (int i = 0; i < numAnswers; i++) {
            JsonNode oneAnswerNode = answerArrayNode.get(i);
            questionNameSet.add(oneAnswerNode.get("item").textValue());
        }
        assertEquals(2, questionNameSet.size());
        assertTrue(questionNameSet.contains("foo"));
        assertTrue(questionNameSet.contains("bar"));

        // We should have no messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    @Test
    public void jsonData() throws Exception {
        // fill in context with JSON data
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"string.json\",\n" +
                "       \"timestamp\":\"2015-04-13T18:48:02-07:00\"\n" +
                "   },{\n" +
                "       \"filename\":\"blob.json\",\n" +
                "       \"timestamp\":\"2015-04-13T18:47:20-07:00\"\n" +
                "   }],\n" +
                "   \"item\":\"json-data\"\n" +
                "}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        String stringJsonText = "{\n" +
                "   \"string\":\"This is a string\"\n" +
                "}";
        JsonNode stringJsonNode = BridgeObjectMapper.get().readTree(stringJsonText);

        String blobJsonText = "{\n" +
                "   \"blob\":[\"This\", \"is\", \"a\", \"blob\"]\n" +
                "}";
        JsonNode blobJsonNode = BridgeObjectMapper.get().readTree(blobJsonText);

        context.setJsonDataMap(ImmutableMap.of(
                "info.json", infoJsonNode,
                "string.json", stringJsonNode,
                "blob.json", blobJsonNode));
        context.setUnzippedDataMap(ImmutableMap.<String, byte[]>of());

        // execute
        handler.handle(context);

        // validate
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        validateCommonRecordProps(recordBuilder);
        assertEquals(DateTime.parse("2015-04-13T18:48:02-07:00").getMillis(),
                recordBuilder.getCreatedOn().longValue());
        assertEquals("json-data", recordBuilder.getSchemaId());
        assertEquals(1, recordBuilder.getSchemaRevision());

        JsonNode dataNode = recordBuilder.getData();
        assertEquals(1, dataNode.size());
        assertEquals("This is a string", dataNode.get("string.json.string").textValue());

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(1, attachmentMap.size());
        JsonNode blobNode = BridgeObjectMapper.get().readTree(attachmentMap.get("blob.json.blob"));
        assertEquals(4, blobNode.size());
        assertEquals("This", blobNode.get(0).textValue());
        assertEquals("is", blobNode.get(1).textValue());
        assertEquals("a", blobNode.get(2).textValue());
        assertEquals("blob", blobNode.get(3).textValue());

        // We should have no messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    @Test
    public void nonJsonData() throws Exception {
        // fill in context
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"jsonFile.json\",\n" +
                "       \"timestamp\":\"2015-04-13T18:58:15-07:00\"\n" +
                "   },{\n" +
                "       \"filename\":\"nonJsonFile.txt\",\n" +
                "       \"timestamp\":\"2015-04-13T18:58:21-07:00\"\n" +
                "   }],\n" +
                "   \"item\":\"non-json-data\"\n" +
                "}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        String jsonJsonText = "{\n" +
                "   \"field\":\"This is JSON data\"\n" +
                "}";
        JsonNode jsonJsonNode = BridgeObjectMapper.get().readTree(jsonJsonText);

        context.setJsonDataMap(ImmutableMap.of(
                "info.json", infoJsonNode,
                "jsonFile.json", jsonJsonNode));
        context.setUnzippedDataMap(ImmutableMap.of("nonJsonFile.txt",
                "This is non-JSON data".getBytes(Charsets.UTF_8)));

        // execute
        handler.handle(context);

        // validate
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        validateCommonRecordProps(recordBuilder);
        assertEquals(DateTime.parse("2015-04-13T18:58:21-07:00").getMillis(),
                recordBuilder.getCreatedOn().longValue());
        assertEquals("non-json-data", recordBuilder.getSchemaId());
        assertEquals(1, recordBuilder.getSchemaRevision());

        JsonNode dataNode = recordBuilder.getData();
        assertEquals(0, dataNode.size());

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(2, attachmentMap.size());

        JsonNode jsonJsonAttachmentNode = BridgeObjectMapper.get().readTree(attachmentMap.get("jsonFile.json"));
        assertEquals(1, jsonJsonAttachmentNode.size());
        assertEquals("This is JSON data", jsonJsonAttachmentNode.get("field").textValue());

        assertEquals("This is non-JSON data", new String(attachmentMap.get("nonJsonFile.txt"), Charsets.UTF_8));

        // We should have no messages.
        assertTrue(context.getMessageList().isEmpty());
    }

    private static void validateCommonRecordProps(HealthDataRecordBuilder recordBuilder) {
        assertEquals(TEST_HEALTHCODE, recordBuilder.getHealthCode());
        assertEquals(TEST_STUDY_ID, recordBuilder.getStudyId());
        assertEquals(TEST_UPLOAD_DATE_STRING, recordBuilder.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals(TEST_UPLOAD_ID, recordBuilder.getUploadId());

        // Don't parse into the metadata. Just check that it exists and is an object node.
        assertTrue(recordBuilder.getMetadata().isObject());
    }
}
