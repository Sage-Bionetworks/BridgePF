package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadArchiveService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadHandlersEndToEndTest {
    private static final String APP_VERSION = "version 1.0.0, build 1";
    private static final Set<String> DATA_GROUP_SET = ImmutableSet.of("parkinson", "test_user");
    private static final String EXTERNAL_ID = "external-id";
    private static final String HEALTH_CODE = "health-code";
    private static final byte[] METADATA_JSON_CONTENT = "{\"my-meta-key\":\"my-meta-value\"}".getBytes();
    private static final String PHONE_INFO = "Unit Test Hardware";
    private static final String UPLOAD_ID = "upload-id";

    private static final String CREATED_ON_STRING = "2015-04-02T03:26:59.456-07:00";
    private static final long CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(CREATED_ON_STRING);
    private static final String CREATED_ON_TIME_ZONE = "-0700";

    private static final DateTime MOCK_NOW = DateTime.parse("2016-06-03T11:33:55.777-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();
    private static final LocalDate MOCK_TODAY = MOCK_NOW.toLocalDate();

    private static final DynamoStudy STUDY = new DynamoStudy();
    static {
        STUDY.setStrictUploadValidationEnabled(true);
    }

    private static final String SCHEMA_ID = "non-survey-schema";
    private static final String SCHEMA_NAME = "Non-Survey Schema";
    private static final int SCHEMA_REV = 3;

    private static final String SURVEY_CREATED_ON_STRING = "2016-06-03T16:01:02.003-0700";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);
    private static final String SURVEY_GUID = "survey-guid";
    private static final String SURVEY_ID = "survey-id";
    private static final String SURVEY_SCHEMA_NAME = "My Survey";
    private static final int SURVEY_SCHEMA_REV = 2;

    private static final DynamoUpload2 UPLOAD = new DynamoUpload2();
    static {
        UPLOAD.setHealthCode(HEALTH_CODE);
        UPLOAD.setUploadId(UPLOAD_ID);
    }

    private InMemoryFileHelper inMemoryFileHelper;
    private HealthDataService mockHealthDataService;
    private UploadDao mockUploadDao;
    private S3Helper mockS3UploadHelper;
    private ArgumentCaptor<ObjectMetadata> metadataCaptor;
    private HealthDataRecord savedRecord;
    private Map<String, byte[]> uploadedFileContentMap;
    private byte[] zippedFile;

    @BeforeClass
    public static void mockDateTime() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Before
    public void before() throws Exception {
        // Reset all member vars, because JUnit doesn't.
        inMemoryFileHelper = new InMemoryFileHelper();
        mockHealthDataService = mock(HealthDataService.class);
        mockUploadDao = mock(UploadDao.class);
        mockS3UploadHelper = mock(S3Helper.class);
        savedRecord = null;
        uploadedFileContentMap = new HashMap<>();
        metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);

        // Mock HealthDataService.createOrUpdateRecord()
        when(mockHealthDataService.createOrUpdateRecord(any(HealthDataRecord.class))).thenAnswer(
                invocation -> {
                    // save record
                    savedRecord = invocation.getArgument(0);
                    return savedRecord.getId();
                });

        when(mockHealthDataService.getRecordById(any())).thenAnswer(invocation -> {
            String recordId = invocation.getArgument(0);
            if (savedRecord == null || !savedRecord.getId().equals(recordId)) {
                return null;
            } else {
                return savedRecord;
            }
        });

        // Mock S3 upload helper. We need to save the file contents, since we delete all files at the end of execution.
        doAnswer(invocation -> {
            String s3Key = invocation.getArgument(1);
            File uploadedFile = invocation.getArgument(2);
            byte[] uploadedFileContent = inMemoryFileHelper.getBytes(uploadedFile);
            uploadedFileContentMap.put(s3Key, uploadedFileContent);

            // Required return
            return null;
        }).when(mockS3UploadHelper).writeFileToS3(any(), any(), any(), any());

        doAnswer(invocation -> {
            String s3Key = invocation.getArgument(1);
            byte[] uploadedFileContent = invocation.getArgument(2);
            uploadedFileContentMap.put(s3Key, uploadedFileContent);

            // Required return
            return null;
        }).when(mockS3UploadHelper).writeBytesToS3(any(), any(), any(), any());
    }

    @AfterClass
    public static void resetDateTime() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    private void test(UploadSchema schema, Survey survey, Map<String, String> fileMap) {
        // fileMap is in strings. Convert to bytes so we can use the Zipper.
        Map<String, byte[]> fileBytesMap = new HashMap<>();
        for (Map.Entry<String, String> oneFile : fileMap.entrySet()) {
            String filename = oneFile.getKey();
            String fileString = oneFile.getValue();
            fileBytesMap.put(filename, fileString.getBytes(Charsets.UTF_8));
        }

        // Add metadata.json to the fileBytesMap.
        fileBytesMap.put("metadata.json", METADATA_JSON_CONTENT);

        // For zipping, we use the real service.
        UploadArchiveService unzipService = new UploadArchiveService();
        unzipService.setMaxNumZipEntries(1000000);
        unzipService.setMaxZipEntrySize(1000000);
        zippedFile = unzipService.zip(fileBytesMap);

        // Set up UploadFileHelper
        UploadFileHelper uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setFileHelper(inMemoryFileHelper);
        uploadFileHelper.setS3Helper(mockS3UploadHelper);

        // set up S3DownloadHandler - mock S3 Helper
        // "S3" returns file unencrypted for simplicity of testing
        S3Helper mockS3DownloadHelper = mock(S3Helper.class);
        doAnswer(invocation -> {
            File destFile = invocation.getArgument(2);
            inMemoryFileHelper.writeBytes(destFile, zippedFile);

            // Required return
            return null;
        }).when(mockS3DownloadHelper).downloadS3File(eq(TestConstants.UPLOAD_BUCKET), eq(UPLOAD_ID), any());

        S3DownloadHandler s3DownloadHandler = new S3DownloadHandler();
        s3DownloadHandler.setFileHelper(inMemoryFileHelper);
        s3DownloadHandler.setS3Helper(mockS3DownloadHelper);

        // set up DecryptHandler - For ease of tests, this will just return the input verbatim.
        UploadArchiveService mockUploadArchiveService = mock(UploadArchiveService.class);
        when(mockUploadArchiveService.decrypt(eq(TestConstants.TEST_STUDY_IDENTIFIER), any(InputStream.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        DecryptHandler decryptHandler = new DecryptHandler();
        decryptHandler.setFileHelper(inMemoryFileHelper);
        decryptHandler.setUploadArchiveService(mockUploadArchiveService);

        // Set up UnzipHandler
        UnzipHandler unzipHandler = new UnzipHandler();
        unzipHandler.setFileHelper(inMemoryFileHelper);
        unzipHandler.setUploadArchiveService(unzipService);

        // Set up InitRecordHandler
        InitRecordHandler initRecordHandler = new InitRecordHandler();
        initRecordHandler.setFileHelper(inMemoryFileHelper);

        // mock schema service
        UploadSchemaService mockUploadSchemaService = mock(UploadSchemaService.class);
        when(mockUploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, schema.getSchemaId(),
                schema.getRevision())).thenReturn(schema);

        // mock survey service
        SurveyService mockSurveyService = mock(SurveyService.class);
        if (survey != null) {
            when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY,
                    new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn()), false, true))
                            .thenReturn(survey);
        }

        // set up IosSchemaValidationHandler
        IosSchemaValidationHandler2 iosSchemaValidationHandler = new IosSchemaValidationHandler2();
        iosSchemaValidationHandler.setFileHelper(inMemoryFileHelper);
        iosSchemaValidationHandler.setUploadFileHelper(uploadFileHelper);
        iosSchemaValidationHandler.setUploadSchemaService(mockUploadSchemaService);
        iosSchemaValidationHandler.setSurveyService(mockSurveyService);

        // set up GenericUploadFormatHandler
        GenericUploadFormatHandler genericUploadFormatHandler = new GenericUploadFormatHandler();
        genericUploadFormatHandler.setFileHelper(inMemoryFileHelper);
        genericUploadFormatHandler.setUploadFileHelper(uploadFileHelper);
        genericUploadFormatHandler.setUploadSchemaService(mockUploadSchemaService);
        genericUploadFormatHandler.setSurveyService(mockSurveyService);

        // set up UploadFormatHandler
        UploadFormatHandler uploadFormatHandler = new UploadFormatHandler();
        uploadFormatHandler.setV1LegacyHandler(iosSchemaValidationHandler);
        uploadFormatHandler.setV2GenericHandler(genericUploadFormatHandler);

        // set up StrictValidationHandler
        StrictValidationHandler strictValidationHandler = new StrictValidationHandler();
        strictValidationHandler.setUploadSchemaService(mockUploadSchemaService);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        strictValidationHandler.setStudyService(mockStudyService);

        // set up TranscribeConsentHandler
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of("parkinson","test_user"));
        account.setExternalId(EXTERNAL_ID);
        account.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);

        AccountDao mockAccountDao = mock(AccountDao.class);
        when(mockAccountDao.getAccount(any())).thenReturn(account);

        TranscribeConsentHandler transcribeConsentHandler = new TranscribeConsentHandler();
        transcribeConsentHandler.setAccountDao(mockAccountDao);

        // mock HealthDataService should return empty list for getRecordsByHealthcodeCreatedOnSchemaId(), so dedupe
        // logic doesn't crash
        when(mockHealthDataService.getRecordsByHealthcodeCreatedOnSchemaId(HEALTH_CODE, CREATED_ON_MILLIS,
                schema.getSchemaId())).thenReturn(ImmutableList.of());

        // Set up UploadRawZipHandler.
        UploadRawZipHandler uploadRawZipHandler = new UploadRawZipHandler();
        uploadRawZipHandler.setS3Helper(mockS3UploadHelper);

        // set up UploadArtifactsHandler
        UploadArtifactsHandler uploadArtifactsHandler = new UploadArtifactsHandler();
        uploadArtifactsHandler.setHealthDataService(mockHealthDataService);

        // set up task factory
        List<UploadValidationHandler> handlerList = ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler,
                initRecordHandler, uploadFormatHandler, strictValidationHandler, transcribeConsentHandler,
                uploadRawZipHandler, uploadArtifactsHandler);

        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setFileHelper(inMemoryFileHelper);
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(mockUploadDao);
        taskFactory.setHealthDataService(mockHealthDataService);

        // create task, execute
        UploadValidationTask task = taskFactory.newTask(TestConstants.TEST_STUDY, UPLOAD);
        task.run();
    }

    private static void validateCommonRecordProps(HealthDataRecord record) {
        // The following fields are not used in this test:
        //   data, schemaId, and schemaRevision fields are specific to individual tests.
        //   id - This one is created by the DAO, so it's not present in the passed in record.
        //   synapseExporterStatus - This isn't used in upload validation.
        //   version - That's internal.
        assertEquals(APP_VERSION, record.getAppVersion());
        assertEquals(CREATED_ON_MILLIS, record.getCreatedOn().longValue());
        assertEquals(CREATED_ON_TIME_ZONE, record.getCreatedOnTimeZone());
        assertEquals(HEALTH_CODE, record.getHealthCode());
        assertEquals(PHONE_INFO, record.getPhoneInfo());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, record.getStudyId());
        assertEquals(MOCK_TODAY, record.getUploadDate());
        assertEquals(UPLOAD_ID, record.getUploadId());
        assertEquals(MOCK_NOW_MILLIS, record.getUploadedOn().longValue());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, record.getUserSharingScope());
        assertEquals(EXTERNAL_ID, record.getUserExternalId());
        assertEquals(DATA_GROUP_SET, record.getUserDataGroups());

        // Metadata needs to exist for backwards compatibility, and it should have appVersion and phoneInfo. Beyond
        // that, it doesn't really matter
        JsonNode metadataNode = record.getMetadata();
        assertEquals(APP_VERSION, metadataNode.get(UploadUtil.FIELD_APP_VERSION).textValue());
        assertEquals(PHONE_INFO, metadataNode.get(UploadUtil.FIELD_PHONE_INFO).textValue());

        // Validate user metadata.
        JsonNode userMetadataNode = record.getUserMetadata();
        assertEquals(1, userMetadataNode.size());
        assertEquals("my-meta-value", userMetadataNode.get("my-meta-key").textValue());
    }

    private void testSurvey(Map<String, String> fileMap) throws Exception {
        // set up schema
        // To test backwards compatibility, survey schema should include both the old style fields and the new
        // "answers" field.
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                UploadUtil.ANSWERS_FIELD_DEF,
                new UploadFieldDefinition.Builder().withName("AAA").withType(UploadFieldType.SINGLE_CHOICE)
                        .build(),
                new UploadFieldDefinition.Builder().withName("BBB").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("fencing", "football", "running", "swimming", "3").build(),
                new UploadFieldDefinition.Builder().withName("delicious").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("Yes", "No").withAllowOtherChoices(true).build());

        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(SURVEY_SCHEMA_NAME);
        schema.setRevision(SURVEY_SCHEMA_REV);
        schema.setSchemaId(SURVEY_ID);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSurveyGuid(SURVEY_GUID);
        schema.setSurveyCreatedOn(SURVEY_CREATED_ON_MILLIS);

        // set up survey (all we need is reference to the schema)
        DynamoSurvey survey = new DynamoSurvey();
        survey.setCreatedOn(SURVEY_CREATED_ON_MILLIS);
        survey.setGuid(SURVEY_GUID);
        survey.setIdentifier(SURVEY_ID);
        survey.setSchemaRevision(SURVEY_SCHEMA_REV);

        // execute
        test(schema, survey, fileMap);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals(SURVEY_ID, record.getSchemaId());
        assertEquals(SURVEY_SCHEMA_REV, record.getSchemaRevision());

        JsonNode dataNode = record.getData();
        assertEquals(4, dataNode.size());
        assertEquals("Yes", dataNode.get("AAA").textValue());

        JsonNode bbbChoiceAnswersNode = dataNode.get("BBB");
        assertEquals(3, bbbChoiceAnswersNode.size());
        assertEquals("fencing", bbbChoiceAnswersNode.get(0).textValue());
        assertEquals("running", bbbChoiceAnswersNode.get(1).textValue());
        assertEquals("3", bbbChoiceAnswersNode.get(2).textValue());

        JsonNode deliciousNode = dataNode.get("delicious");
        assertEquals(2, deliciousNode.size());
        assertEquals("Yes", deliciousNode.get(0).textValue());
        assertEquals("Maybe", deliciousNode.get(1).textValue());

        // Answers node has all the same fields as dataNode, except without its own answers field. Note that the
        // answers field doesn't go through canonicalization, since it's treated as an attachment instead of individual
        // fields.
        String answersAttachmentId = dataNode.get(UploadUtil.FIELD_ANSWERS).textValue();
        byte[] answersUploadedContent = uploadedFileContentMap.get(answersAttachmentId);
        JsonNode answersNode = BridgeObjectMapper.get().readTree(answersUploadedContent);
        assertEquals(3, answersNode.size());

        JsonNode aaaChoiceAnswersNode = answersNode.get("AAA");
        assertEquals(1, aaaChoiceAnswersNode.size());
        assertEquals("Yes", aaaChoiceAnswersNode.get(0).textValue());

        bbbChoiceAnswersNode = answersNode.get("BBB");
        assertEquals(3, bbbChoiceAnswersNode.size());
        assertEquals("fencing", bbbChoiceAnswersNode.get(0).textValue());
        assertEquals("running", bbbChoiceAnswersNode.get(1).textValue());
        assertEquals(3, bbbChoiceAnswersNode.get(2).intValue());

        deliciousNode = answersNode.get("delicious");
        assertEquals(2, deliciousNode.size());
        assertEquals("Yes", deliciousNode.get(0).textValue());
        assertEquals("Maybe", deliciousNode.get(1).textValue());

        // We upload the unencrypted zipped file back to S3.
        validateRawDataAttachment();

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(UPLOAD, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    @Test
    public void v1LegacySurvey() throws Exception {
        // set up upload files
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"AAA.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"BBB.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"delicious.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        String aaaJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"choiceAnswers\":[\"Yes\"],\n" +
                "   \"startDate\":\"2015-04-02T03:26:57-07:00\",\n" +
                "   \"questionTypeName\":\"SingleChoice\",\n" +
                "   \"item\":\"AAA\",\n" +
                "   \"endDate\":\"2015-04-02T03:26:59-07:00\"\n" +
                "}";

        String bbbJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"choiceAnswers\":[\"fencing\", \"running\", 3],\n" +
                "   \"startDate\":\"2015-04-02T03:27:05-07:00\",\n" +
                "   \"questionTypeName\":\"MultipleChoice\",\n" +
                "   \"item\":\"BBB\",\n" +
                "   \"endDate\":\"2015-04-02T03:27:09-07:00\"\n" +
                "}";

        String deliciousJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"choiceAnswers\":[\"Yes\", \"Maybe\"],\n" +
                "   \"startDate\":\"2015-04-02T03:27:05-07:00\",\n" +
                "   \"questionTypeName\":\"MultipleChoice\",\n" +
                "   \"item\":\"delicious\",\n" +
                "   \"endDate\":\"2015-04-02T03:27:09-07:00\"\n" +
                "}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("AAA.json", aaaJsonText).put("BBB.json", bbbJsonText).put("delicious.json", deliciousJsonText)
                .build();

        // execute
        testSurvey(fileMap);
    }

    @Test
    public void v2GenericSurvey() throws Exception {
        // set up upload files
        String infoJsonText = "{\n" +
                "   \"createdOn\":\"" + CREATED_ON_STRING + "\",\n" +
                "   \"dataFilename\":\"answers.json\",\n" +
                "   \"format\":\"v2_generic\",\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        String answersJsonText = "{\n" +
                "   \"AAA\":[\"Yes\"],\n" +
                "   \"BBB\":[\"fencing\", \"running\", 3],\n" +
                "   \"delicious\":[\"Yes\", \"Maybe\"]\n" +
                "}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("answers.json", answersJsonText).build();

        // execute
        testSurvey(fileMap);
    }

    private void testNonSurvey(String infoJsonText) throws Exception {
        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("CCC.txt").withType(UploadFieldType.ATTACHMENT_BLOB)
                        .build(),
                new UploadFieldDefinition.Builder().withName("DDD.csv").withType(UploadFieldType.ATTACHMENT_CSV)
                        .build(),
                new UploadFieldDefinition.Builder().withName("EEE.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build(),
                new UploadFieldDefinition.Builder().withName("FFF.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_TABLE).build(),
                new UploadFieldDefinition.Builder().withName("GGG.txt").withType(UploadFieldType.ATTACHMENT_V2)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.HHH")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new UploadFieldDefinition.Builder().withName("record.json.III").withType(UploadFieldType.BOOLEAN)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.JJJ")
                        .withType(UploadFieldType.CALENDAR_DATE).build(),
                new UploadFieldDefinition.Builder().withName("record.json.LLL")
                        .withType(UploadFieldType.DURATION_V2).build(),
                new UploadFieldDefinition.Builder().withName("record.json.MMM").withType(UploadFieldType.FLOAT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.NNN")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build(),
                new UploadFieldDefinition.Builder().withName("record.json.OOO").withType(UploadFieldType.INT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.PPP").withType(UploadFieldType.STRING)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.QQQ").withType(UploadFieldType.TIME_V2)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.arrr")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new UploadFieldDefinition.Builder().withName("empty_attachment")
                        .withType(UploadFieldType.ATTACHMENT_V2).withRequired(false).build());

        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(SCHEMA_NAME);
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaId(SCHEMA_ID);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        // set up upload files
        String cccTxtContent = "Blob file";

        String dddCsvContent = "foo,bar\nbaz,qux";

        String eeeJsonContent = "{\"key\":\"value\"}";

        String fffJsonContent = "[{\"name\":\"Dwayne\"},{\"name\":\"Eggplant\"}]";

        String gggTxtContent = "Arbitrary attachment";

        // Note that a lot of these have the wrong type, but are convertible to the correct type. This is to test that
        // values can be canonicalized.
        String recordJsonContent = "{\n" +
                "   \"HHH\":[\"attachment\", \"inside\", \"file\"],\n" +
                "   \"III\":1,\n" +
                "   \"JJJ\":\"2016-06-03T17:03-0700\",\n" +
                "   \"LLL\":\"PT1H\",\n" +
                "   \"MMM\":\"3.14\",\n" +
                "   \"NNN\":[\"inline\", \"json\"],\n" +
                "   \"OOO\":\"2.718\",\n" +
                "   \"PPP\":1337,\n" +
                "   \"QQQ\":\"2016-06-03T19:21:35.378-0700\",\n" +
                "   \"arrr\":\"2016-06-03T18:12:34.567+0900\"\n" +
                "}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("CCC.txt", cccTxtContent).put("DDD.csv", dddCsvContent).put("EEE.json", eeeJsonContent)
                .put("FFF.json", fffJsonContent).put("GGG.txt", gggTxtContent).put("record.json", recordJsonContent)
                .put("empty_attachment", "").build();

        // execute
        test(schema, null, fileMap);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals(SCHEMA_ID, record.getSchemaId());
        assertEquals(SCHEMA_REV, record.getSchemaRevision());

        JsonNode dataNode = record.getData();
        assertEquals(15, dataNode.size());
        assertTrue(dataNode.get("record.json.III").booleanValue());
        assertEquals("2016-06-03", dataNode.get("record.json.JJJ").textValue());
        assertEquals("PT1H", dataNode.get("record.json.LLL").textValue());
        assertEquals(3.14, dataNode.get("record.json.MMM").doubleValue(), /*delta*/ 0.001);
        assertEquals(2, dataNode.get("record.json.OOO").intValue());
        assertEquals("1337", dataNode.get("record.json.PPP").textValue());
        assertEquals("19:21:35.378", dataNode.get("record.json.QQQ").textValue());
        assertEquals(DateTime.parse("2016-06-03T18:12:34.567+0900"),
                DateTime.parse(dataNode.get("record.json.arrr").textValue()));

        JsonNode nnnNode = dataNode.get("record.json.NNN");
        assertEquals(2, nnnNode.size());
        assertEquals("inline", nnnNode.get(0).textValue());
        assertEquals("json", nnnNode.get(1).textValue());

        // validate attachment content in S3
        String cccTxtAttachmentId = dataNode.get("CCC.txt").textValue();
        validateTextAttachment(cccTxtContent, cccTxtAttachmentId);

        String dddCsvAttachmentId = dataNode.get("DDD.csv").textValue();
        validateTextAttachment(dddCsvContent, dddCsvAttachmentId);

        String gggTxtAttachmentId = dataNode.get("GGG.txt").textValue();
        validateTextAttachment(gggTxtContent, gggTxtAttachmentId);

        String eeeJsonAttachmentId = dataNode.get("EEE.json").textValue();
        byte[] eeeJsonUploadedContent = uploadedFileContentMap.get(eeeJsonAttachmentId);
        JsonNode eeeJsonNode = BridgeObjectMapper.get().readTree(eeeJsonUploadedContent);
        assertEquals(1, eeeJsonNode.size());
        assertEquals("value", eeeJsonNode.get("key").textValue());

        String fffJsonAttachmentId = dataNode.get("FFF.json").textValue();
        byte[] fffJsonUploadedContent = uploadedFileContentMap.get(fffJsonAttachmentId);
        JsonNode fffJsonNode = BridgeObjectMapper.get().readTree(fffJsonUploadedContent);
        assertEquals(2, fffJsonNode.size());

        assertEquals(1, fffJsonNode.get(0).size());
        assertEquals("Dwayne", fffJsonNode.get(0).get("name").textValue());

        assertEquals(1, fffJsonNode.get(1).size());
        assertEquals("Eggplant", fffJsonNode.get(1).get("name").textValue());

        String hhhAttachmentId = dataNode.get("record.json.HHH").textValue();
        
        ArgumentCaptor<byte[]> attachmentContentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockS3UploadHelper).writeBytesToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(hhhAttachmentId),
                attachmentContentCaptor.capture(), metadataCaptor.capture());
        JsonNode hhhNode = BridgeObjectMapper.get().readTree(attachmentContentCaptor.getValue());        
        assertEquals(3, hhhNode.size());
        assertEquals("attachment", hhhNode.get(0).textValue());
        assertEquals("inside", hhhNode.get(1).textValue());
        assertEquals("file", hhhNode.get(2).textValue());
        
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());

        // We upload the unencrypted zipped file back to S3.
        validateRawDataAttachment();

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(UPLOAD, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    @Test
    public void v1LegacyNonSurvey() throws Exception {
        // make info.json
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"CCC.txt\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"DDD.csv\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"EEE.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"FFF.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"GGG.txt\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"record.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"empty_attachment\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"item\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        // execute
        testNonSurvey(infoJsonText);
    }

    @Test
    public void v2GenericNonSurvey() throws Exception {
        // make info.json
        String infoJsonText = "{\n" +
                "   \"createdOn\":\"" + CREATED_ON_STRING + "\",\n" +
                "   \"format\":\"v2_generic\",\n" +
                "   \"item\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        // execute
        testNonSurvey(infoJsonText);
    }

    private void validateTextAttachment(String expected, String attachmentId) {
        byte[] uploadedFileContent = uploadedFileContentMap.get(attachmentId);
        assertEquals(expected, new String(uploadedFileContent, Charsets.UTF_8));
    }

    private void validateRawDataAttachment() {
        String expectedRawDataAttachmentId = UPLOAD_ID + "-raw.zip";
        verify(mockS3UploadHelper).writeFileToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(expectedRawDataAttachmentId),
                any(), metadataCaptor.capture());
        byte[] rawDataBytes = uploadedFileContentMap.get(expectedRawDataAttachmentId);
        assertArrayEquals(zippedFile, rawDataBytes);
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
    }
}
