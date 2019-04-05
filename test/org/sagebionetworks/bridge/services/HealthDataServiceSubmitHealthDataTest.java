package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadFileHelper;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.upload.UploadValidationContext;
import org.sagebionetworks.bridge.upload.UploadValidationException;

public class HealthDataServiceSubmitHealthDataTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final JsonNode ATTACHMENT_ID_NODE = TextNode.valueOf("dummy-attachment-id");
    private static final JsonNode DATA = BridgeObjectMapper.get().createObjectNode();
    private static final String HEALTH_CODE = "test-health-code";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2017-09-07T15:02:56.756+0900");
    private static final long SURVEY_CREATED_ON_MILLIS = SURVEY_CREATED_ON.getMillis();
    private static final String SURVEY_GUID = "test-survey-guid";

    private static final DateTime CREATED_ON = DateTime.parse("2017-08-24T14:38:57.340+0900");
    private static final long CREATED_ON_MILLIS = CREATED_ON.getMillis();
    private static final String CREATED_ON_TIMEZONE = "+0900";

    private static final LocalDate MOCK_NOW_DATE = LocalDate.parse("2017-05-19");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();

    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .build();

    private HealthDataRecord createdRecord;
    private S3Helper mockS3Helper;
    private SurveyService mockSurveyService;
    private StrictValidationHandler mockStrictValidationHandler;
    private TranscribeConsentHandler mockTranscribeConsentHandler;
    private UploadArtifactsHandler mockUploadArtifactsHandler;
    private UploadFileHelper mockUploadFileHelper;
    private HealthDataService svc;
    private UploadSchema schema;
    private Survey survey;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void before() throws Exception {
        // Mock Schema Service.
        schema = UploadSchema.create();
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaId(SCHEMA_ID);
        schema.setRevision(SCHEMA_REV);

        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                schema);

        // Mock survey service.
        survey = Survey.create();
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON_MILLIS);
        survey.setIdentifier(SCHEMA_ID);
        survey.setSchemaRevision(SCHEMA_REV);

        mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY,
                new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS), false, true))
                .thenReturn(survey);

        // Mock upload file helper.
        mockUploadFileHelper = mock(UploadFileHelper.class);
        when(mockUploadFileHelper.uploadJsonNodeAsAttachment(any(), any(), any())).thenReturn(ATTACHMENT_ID_NODE);

        // Mock other dependencies.
        mockS3Helper = mock(S3Helper.class);
        mockStrictValidationHandler = mock(StrictValidationHandler.class);
        mockTranscribeConsentHandler = mock(TranscribeConsentHandler.class);
        mockUploadArtifactsHandler = mock(UploadArtifactsHandler.class);

        // UploadArtifactsHandler needs to write record ID back into the context.
        doAnswer(invocation -> {
            UploadValidationContext context = invocation.getArgument(0);
            HealthDataRecord record = context.getHealthDataRecord();
            record.setId(context.getUploadId());
            context.setRecordId(context.getUploadId());
            return null;
        }).when(mockUploadArtifactsHandler).handle(any());

        // Set up service.
        svc = spy(new HealthDataService());
        svc.setS3Helper(mockS3Helper);
        svc.setSchemaService(mockSchemaService);
        svc.setSurveyService(mockSurveyService);
        svc.setUploadFileHelper(mockUploadFileHelper);
        svc.setStrictValidationHandler(mockStrictValidationHandler);
        svc.setTranscribeConsentHandler(mockTranscribeConsentHandler);
        svc.setUploadArtifactsHandler(mockUploadArtifactsHandler);

        // Spy getRecordById(). This decouples the submitHealthData implementation from the getRecord implementation.
        // At this point, we only care about data flow. Don't worry about the actual content.
        createdRecord = HealthDataRecord.create();
        doReturn(createdRecord).when(svc).getRecordById(any());
    }

    @Test(expected = InvalidEntityException.class)
    public void nullSubmission() throws Exception {
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidSubmission() throws Exception {
        HealthDataSubmission submission = makeValidBuilderWithSchema().withData(null).build();
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);
    }

    @Test
    public void submitHealthDataBySchema() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("sanitize____this").withType(UploadFieldType.STRING)
                        .build(),
                new UploadFieldDefinition.Builder().withName("no-value-field").withType(UploadFieldType.STRING)
                        .withRequired(false).build(),
                new UploadFieldDefinition.Builder().withName("null-value-field").withType(UploadFieldType.STRING)
                        .withRequired(false).build(),
                new UploadFieldDefinition.Builder().withName("attachment-field")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new UploadFieldDefinition.Builder().withName("normal-field").withType(UploadFieldType.STRING).build());
        schema.setFieldDefinitions(fieldDefList);

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("sanitize!@#$this", "sanitize this value");
        inputData.putNull("null-value-field");
        inputData.put("attachment-field", "attachment field value");
        inputData.put("normal-field", "normal field value");
        inputData.put("non-schema-field", "this is not in the schema");

        ObjectNode inputMetadata = BridgeObjectMapper.get().createObjectNode();
        inputMetadata.put("sample-metadata-key", "sample-metadata-value");

        HealthDataSubmission submission = makeValidBuilderWithSchema().withData(inputData).withMetadata(inputMetadata)
                .build();

        // execute
        HealthDataRecord svcOutputRecord = svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);

        // verify that we return the record returned by the internal getRecordById() call.
        assertSame(createdRecord, svcOutputRecord);

        // Verify strict validation handler called. While we're at it, verify that we constructed the context and
        // record correctly.
        ArgumentCaptor<UploadValidationContext> contextCaptor = ArgumentCaptor.forClass(UploadValidationContext.class);
        verify(mockStrictValidationHandler).handle(contextCaptor.capture());

        UploadValidationContext context = contextCaptor.getValue();
        assertEquals(HEALTH_CODE, context.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY, context.getStudy());

        // We generate an upload ID and use it for the record ID.
        String uploadId = context.getUploadId();
        assertNotNull(uploadId);
        assertEquals(uploadId, context.getRecordId());

        // We have one attachment. This is text because we passed in text. This will normally be arrays or objects.
        ArgumentCaptor<JsonNode> attachmentNodeCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(mockUploadFileHelper).uploadJsonNodeAsAttachment(attachmentNodeCaptor.capture(), eq(uploadId),
                eq("attachment-field"));
        JsonNode attachmentNode = attachmentNodeCaptor.getValue();
        assertEquals("attachment field value", attachmentNode.textValue());

        // validate the created record
        HealthDataRecord contextRecord = context.getHealthDataRecord();
        assertEquals(APP_VERSION, contextRecord.getAppVersion());
        assertEquals(PHONE_INFO, contextRecord.getPhoneInfo());
        assertEquals(SCHEMA_ID, contextRecord.getSchemaId());
        assertEquals(SCHEMA_REV, contextRecord.getSchemaRevision());
        assertEquals(HEALTH_CODE, contextRecord.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, contextRecord.getStudyId());
        assertEquals(MOCK_NOW_DATE, contextRecord.getUploadDate());
        assertEquals(MOCK_NOW_MILLIS, contextRecord.getUploadedOn().longValue());
        assertEquals(CREATED_ON_MILLIS, contextRecord.getCreatedOn().longValue());
        assertEquals(CREATED_ON_TIMEZONE, contextRecord.getCreatedOnTimeZone());

        // validate the sanitized data (includes attachments with attachment ID)
        JsonNode sanitizedData = contextRecord.getData();
        assertEquals(3, sanitizedData.size());
        assertEquals("sanitize this value", sanitizedData.get("sanitize____this").textValue());
        assertEquals(ATTACHMENT_ID_NODE, sanitizedData.get("attachment-field"));
        assertEquals("normal field value", sanitizedData.get("normal-field").textValue());

        // validate app version and phone info in metadata
        JsonNode metadata = contextRecord.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals(APP_VERSION, metadata.get(UploadUtil.FIELD_APP_VERSION).textValue());
        assertEquals(PHONE_INFO, metadata.get(UploadUtil.FIELD_PHONE_INFO).textValue());

        // validate client-submitted metadata (userMetadata)
        JsonNode userMetadata = contextRecord.getUserMetadata();
        assertEquals(1, userMetadata.size());
        assertEquals("sample-metadata-value", userMetadata.get("sample-metadata-key").textValue());

        // Validate raw data submitted to S3
        String expectedRawDataAttachmentId = uploadId + HealthDataService.RAW_ATTACHMENT_SUFFIX;
        ArgumentCaptor<byte[]> rawBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Helper).writeBytesToS3(eq(HealthDataService.ATTACHMENT_BUCKET), eq(expectedRawDataAttachmentId),
                rawBytesCaptor.capture(), metadataCaptor.capture());
        assertEquals(expectedRawDataAttachmentId, contextRecord.getRawDataAttachmentId());

        byte[] rawBytes = rawBytesCaptor.getValue();
        JsonNode rawJsonNode = BridgeObjectMapper.get().readTree(rawBytes);
        assertEquals(inputData, rawJsonNode);
        
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());

        // validate the other handlers are called
        verify(mockTranscribeConsentHandler).handle(context);
        verify(mockUploadArtifactsHandler).handle(context);

        // We get the record back using the upload ID.
        verify(svc).getRecordById(uploadId);
    }

    @Test
    public void submitHealthDataBySurvey() throws Exception {
        // mock schema service
        // For backwards compatibility, the schema will have both the old per-question fields and the new answers
        // field.
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("answer-me").withType(UploadFieldType.SINGLE_CHOICE)
                        .build(),
                UploadUtil.ANSWERS_FIELD_DEF);
        schema.setFieldDefinitions(fieldDefList);

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("answer-me", "C");
        HealthDataSubmission submission = makeValidBuilderWithSurvey().withData(inputData).build();

        // execute
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);

        // Verify that the we wrote the correct schemaId/Rev to the record. (Everything else is already tested in a
        // previous test case.)
        ArgumentCaptor<UploadValidationContext> contextCaptor = ArgumentCaptor.forClass(UploadValidationContext.class);
        verify(mockStrictValidationHandler).handle(contextCaptor.capture());

        UploadValidationContext context = contextCaptor.getValue();
        HealthDataRecord contextRecord = context.getHealthDataRecord();
        assertEquals(SCHEMA_ID, contextRecord.getSchemaId());
        assertEquals(SCHEMA_REV, contextRecord.getSchemaRevision());

        // validate that our record was parsed correctly
        JsonNode recordData = contextRecord.getData();
        assertEquals(2, recordData.size());
        assertEquals("C", recordData.get("answer-me").textValue());
        assertEquals(ATTACHMENT_ID_NODE, recordData.get(UploadUtil.FIELD_ANSWERS));

        // Verify "answers" survey answers attachment.
        ArgumentCaptor<JsonNode> answersNodeCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(mockUploadFileHelper).uploadJsonNodeAsAttachment(answersNodeCaptor.capture(),
                eq(context.getUploadId()), eq(UploadUtil.FIELD_ANSWERS));
        JsonNode answersNode = answersNodeCaptor.getValue();
        assertEquals(1, answersNode.size());
        assertEquals("C", answersNode.get("answer-me").textValue());

        // validate we did in fact call SurveyService
        verify(mockSurveyService).getSurvey(TestConstants.TEST_STUDY,
                new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS), false, true);
    }

    @Test(expected = EntityNotFoundException.class)
    public void surveyWithoutSchema() throws Exception {
        // Survey has no schema.
        survey.setSchemaRevision(null);

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("answer-me", "C");
        HealthDataSubmission submission = makeValidBuilderWithSurvey().withData(inputData).build();

        // execute
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);
    }

    @Test(expected = BadRequestException.class)
    public void strictValidationThrows() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("simple-field").withType(UploadFieldType.INT).build());
        schema.setFieldDefinitions(fieldDefList);

        // mock handlers - Only StrictValidationHandler will be called. Also, since we're not calling the actual
        // StrictValidationHandler, we need to make it throw.
        doThrow(UploadValidationException.class).when(mockStrictValidationHandler).handle(any());

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("simple-field", "not an int");
        HealthDataSubmission submission = makeValidBuilderWithSchema().withData(inputData).build();

        // execute - This throws.
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithSchema() {
        return makeValidBuilderWithoutSchemaOrSurvey().withSchemaId(SCHEMA_ID).withSchemaRevision(SCHEMA_REV);
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithSurvey() {
        return makeValidBuilderWithoutSchemaOrSurvey().withSurveyGuid(SURVEY_GUID)
                .withSurveyCreatedOn(SURVEY_CREATED_ON);
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithoutSchemaOrSurvey() {
        return new HealthDataSubmission.Builder().withAppVersion(APP_VERSION).withCreatedOn(CREATED_ON).withData(DATA)
                .withPhoneInfo(PHONE_INFO);
    }
}
