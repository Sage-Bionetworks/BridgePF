package org.sagebionetworks.bridge.upload;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

public class IosSchemaContextValidatorTest {
    private static final String TEST_SCHEMA_ID = "test-schema";
    private static final int TEST_SCHEMA_REV = 42;

    @Test(expected = UploadValidationException.class)
    public void nullProdRecordBuilder() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.setHealthDataRecordBuilder(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullTestRecordBuilder() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        testContext.setHealthDataRecordBuilder(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullProdSchemaId() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.getHealthDataRecordBuilder().withSchemaId(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullTestSchemaId() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        testContext.getHealthDataRecordBuilder().withSchemaId(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void mismatchedSchemaIds() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.getHealthDataRecordBuilder().withSchemaId("prod-test-schema");
        testContext.getHealthDataRecordBuilder().withSchemaId("test-test-schema");

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void mismatchedSchemaRevs() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.getHealthDataRecordBuilder().withSchemaRevision(13);
        testContext.getHealthDataRecordBuilder().withSchemaRevision(17);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullProdData() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.getHealthDataRecordBuilder().withData(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullTestData() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        testContext.getHealthDataRecordBuilder().withData(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void extraProdDataFields() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        ((ObjectNode) prodContext.getHealthDataRecordBuilder().getData()).put("prod-field", "asdf");

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void extraTestDataFields() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        ((ObjectNode) testContext.getHealthDataRecordBuilder().getData()).put("test-field", "jkl;");

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullProdAttachments() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.setAttachmentsByFieldName(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void nullTestAttachments() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        testContext.setAttachmentsByFieldName(null);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void extraProdAttachments() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        prodContext.getAttachmentsByFieldName().put("prod-attachment",
                "This is in prod but not test".getBytes(Charsets.UTF_8));

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test(expected = UploadValidationException.class)
    public void extraTestAttachments() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();
        testContext.getAttachmentsByFieldName().put("prod-attachment",
                "This is in prod but not test".getBytes(Charsets.UTF_8));

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test
    public void happyCase() throws Exception {
        // make contexts
        UploadValidationContext prodContext = makeNonSurvey();
        UploadValidationContext testContext = makeNonSurvey();

        // execute
        IosSchemaContextValidator.INSTANCE.validate(prodContext, testContext);
    }

    @Test
    public void survey() throws Exception {
        // It doesn't matter what we fill in, since the validator ignores anything with the prod schema ID
        // "ios-survey", so just fill in something close to what looks right (but different between v1 and v2) and make
        // sure the validator doesn't fail it.

        // make v1 (prod) context
        String v1DataText = "{\"item\":\"test-survey\", \"taskRunId\":\"test-taskRun\"}";
        JsonNode v1DataNode = BridgeObjectMapper.get().readTree(v1DataText);

        HealthDataRecordBuilder v1RecordBuilder = new DynamoHealthDataRecord.Builder().withData(v1DataNode)
                .withSchemaId(IosSchemaValidationHandler.SCHEMA_IOS_SURVEY).withSchemaRevision(1);

        String v1AnswerText = "[\n" +
                "   {\n" +
                "       \"textAnswer\":\"foo answer\",\n" +
                "       \"questionTypeName\":\"Text\",\n" +
                "       \"item\":\"foo\"\n" +
                "   },{\n" +
                "       \"numericAnswer\":42,\n" +
                "       \"unit\":\"lb\",\n" +
                "       \"questionTypeName\":\"Integer\",\n" +
                "       \"item\":\"bar\"\n" +
                "   },{\n" +
                "       \"choiceAnswers\":[\"survey\", \"blob\"],\n" +
                "       \"questionTypeName\":\"MultipleChoice\",\n" +
                "       \"item\":\"baz\"\n" +
                "   }\n" +
                "]";
        Map<String, byte[]> v1AttachmentMap = ImmutableMap.of("answers", v1AnswerText.getBytes(Charsets.UTF_8));

        UploadValidationContext v1Context = new UploadValidationContext();
        v1Context.setHealthDataRecordBuilder(v1RecordBuilder);
        v1Context.setAttachmentsByFieldName(v1AttachmentMap);

        // make v2 (test) context
        String v2DataText = "{\"foo\":\"foo answer\", \"bar\":42, \"bar_unit\":\"lb\"}";
        JsonNode v2DataNode = BridgeObjectMapper.get().readTree(v2DataText);

        HealthDataRecordBuilder v2RecordBuilder = new DynamoHealthDataRecord.Builder().withData(v2DataNode)
                .withSchemaId("test-survey").withSchemaRevision(2);

        Map<String, byte[]> v2AttachmentMap = ImmutableMap.of(
                "baz", "[\"survey\", \"blob\"]".getBytes(Charsets.UTF_8));

        UploadValidationContext v2Context = new UploadValidationContext();
        v2Context.setHealthDataRecordBuilder(v2RecordBuilder);
        v2Context.setAttachmentsByFieldName(v2AttachmentMap);

        // execute
        IosSchemaContextValidator.INSTANCE.validate(v1Context, v2Context);
    }

    // Only makes record builders and attachments maps, since that's all we care about. Furthermore, in the record
    // builder, it only sets the schema ID, schema rev, and data node, since that's all we care about.
    private static UploadValidationContext makeNonSurvey() throws Exception {
        // data node
        String dataText = "{\"foo\":\"foo\", \"bar\":\"bar\"}";
        JsonNode dataNode = BridgeObjectMapper.get().readTree(dataText);

        // record builder
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder().withData(dataNode)
                .withSchemaId(TEST_SCHEMA_ID).withSchemaRevision(TEST_SCHEMA_REV);

        // attachment map - Make this mutable so we can add to it as part of our tests.
        Map<String, byte[]> attachmentMap = new HashMap<>();
        attachmentMap.put("commonFile.txt", "This text is in both prod and text".getBytes(Charsets.UTF_8));

        // create context
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthDataRecordBuilder(recordBuilder);
        context.setAttachmentsByFieldName(attachmentMap);
        return context;

    }
}
