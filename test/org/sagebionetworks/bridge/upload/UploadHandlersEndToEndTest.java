package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadArchiveService;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.sagebionetworks.bridge.util.Zipper;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class UploadHandlersEndToEndTest {
    private static final String ATTACHMENT_ID_PREFIX = "attachment-";
    private static final Set<String> DATA_GROUP_SET = ImmutableSet.of("parkinson", "test_user");
    private static final String EXTERNAL_ID = "external-id";
    private static final String HEALTH_CODE = "health-code";
    private static final String RECORD_ID = "record-id";
    private static final String UPLOAD_ID = "upload-id";
    private static final Zipper ZIPPER = new Zipper(1000000, 1000000);

    private static final String CREATED_ON_STRING = "2015-04-02T03:26:59.456-07:00";
    private static final long CREATED_ON_MILLIS = DateTime.parse(CREATED_ON_STRING).getMillis();

    private static final DateTime MOCK_NOW = DateTime.parse("2016-06-03T11:33:55.777-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();
    private static final LocalDate MOCK_TODAY = MOCK_NOW.toLocalDate();

    private static final Map<String, String> PARTICIPANT_OPTIONS_MAP = ImmutableMap.<String, String>builder()
            .put(ParticipantOption.DATA_GROUPS.name(), "parkinson,test_user")
            .put(ParticipantOption.EXTERNAL_IDENTIFIER.name(), EXTERNAL_ID)
            .put(ParticipantOption.SHARING_SCOPE.name(), ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS.name())
            .build();
    private static final ParticipantOptionsLookup PARTICIPANT_OPTIONS_LOOKUP = new ParticipantOptionsLookup(
            PARTICIPANT_OPTIONS_MAP);

    private static final DynamoStudy STUDY = new DynamoStudy();
    static {
        STUDY.setStrictUploadValidationEnabled(true);
    }

    private static final DynamoUpload2 UPLOAD = new DynamoUpload2();
    static {
        UPLOAD.setHealthCode(HEALTH_CODE);
        UPLOAD.setUploadId(UPLOAD_ID);
    }

    // The following handlers have no external dependencies. We can use real handlers with real helper class objects.
    private static final UnzipHandler UNZIP_HANDLER = new UnzipHandler();
    static {
        UNZIP_HANDLER.setUploadArchiveService(new UploadArchiveService());
    }

    private static final ParseJsonHandler PARSE_JSON_HANDLER = new ParseJsonHandler();

    private int numAttachments;
    private HealthDataService mockHealthDataService;
    private UploadDao mockUploadDao;
    private S3Helper mockS3UploadHelper;
    private HealthDataRecord savedRecord;

    @BeforeClass
    public static void mockDateTime() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Before
    public void before() {
        // Reset all member vars, because JUnit doesn't.
        numAttachments = 0;
        mockHealthDataService = mock(HealthDataService.class);
        mockUploadDao = mock(UploadDao.class);
        mockS3UploadHelper = mock(S3Helper.class);
        savedRecord = null;
    }

    @AfterClass
    public static void resetDateTime() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    private void test(UploadSchema schema, Survey survey, Map<String, String> fileMap) throws Exception {
        // fileMap is in strings. Convert to bytes so we can use the Zipper.
        Map<String, byte[]> fileBytesMap = new HashMap<>();
        for (Map.Entry<String, String> oneFile : fileMap.entrySet()) {
            String filename = oneFile.getKey();
            String fileString = oneFile.getValue();
            fileBytesMap.put(filename, fileString.getBytes(Charsets.UTF_8));
        }

        // zip file
        byte[] zippedFile = ZIPPER.zip(fileBytesMap);

        // set up S3DownloadHandler - mock S3 Helper
        // "S3" returns file unencrypted for simplicity of testing
        S3Helper mockS3DownloadHelper = mock(S3Helper.class);
        when(mockS3DownloadHelper.readS3FileAsBytes(TestConstants.UPLOAD_BUCKET, UPLOAD_ID)).thenReturn(zippedFile);

        S3DownloadHandler s3DownloadHandler = new S3DownloadHandler();
        s3DownloadHandler.setS3Helper(mockS3DownloadHelper);

        // set up DecryptHandler - For ease of tests, this will just return the input verbatim.
        UploadArchiveService mockUploadArchiveService = mock(UploadArchiveService.class);
        when(mockUploadArchiveService.decrypt(TestConstants.TEST_STUDY_IDENTIFIER, zippedFile)).thenReturn(zippedFile);

        DecryptHandler decryptHandler = new DecryptHandler();
        decryptHandler.setUploadArchiveService(mockUploadArchiveService);

        // mock schema service
        UploadSchemaService mockUploadSchemaService = mock(UploadSchemaService.class);
        when(mockUploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, schema.getSchemaId(),
                schema.getRevision())).thenReturn(schema);

        // set up IosSchemaValidationHandler
        IosSchemaValidationHandler2 iosSchemaValidationHandler = new IosSchemaValidationHandler2();
        iosSchemaValidationHandler.setUploadSchemaService(mockUploadSchemaService);

        if (survey != null) {
            SurveyService mockSurveyService = mock(SurveyService.class);
            when(mockSurveyService.getSurvey(new GuidCreatedOnVersionHolderImpl(survey.getGuid(),
                    survey.getCreatedOn()))).thenReturn(survey);
            iosSchemaValidationHandler.setSurveyService(mockSurveyService);
        }

        // health data dao is only used for getBuilder(), so we can just create one without any depedencies
        iosSchemaValidationHandler.setHealthDataDao(new DynamoHealthDataDao());

        // set up StrictValidationHandler
        StrictValidationHandler strictValidationHandler = new StrictValidationHandler();
        strictValidationHandler.setUploadSchemaService(mockUploadSchemaService);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        strictValidationHandler.setStudyService(mockStudyService);

        // set up TranscribeConsentHandler
        ParticipantOptionsService mockOptionsService = mock(ParticipantOptionsService.class);
        when(mockOptionsService.getOptions(HEALTH_CODE)).thenReturn(PARTICIPANT_OPTIONS_LOOKUP);

        TranscribeConsentHandler transcribeConsentHandler = new TranscribeConsentHandler();
        transcribeConsentHandler.setOptionsService(mockOptionsService);

        // mock HealthDataService for UploadArtifactsHandler
        when(mockHealthDataService.createOrUpdateAttachment(any(HealthDataAttachment.class))).thenAnswer(
                invocation -> ATTACHMENT_ID_PREFIX + (++numAttachments));

        when(mockHealthDataService.createOrUpdateRecord(any(HealthDataRecord.class))).thenAnswer(invocation -> {
            // add record ID to record
            HealthDataRecord submittedRecord = invocation.getArgumentAt(0, HealthDataRecord.class);
            savedRecord = new DynamoHealthDataRecord.Builder().copyOf(submittedRecord).withId(RECORD_ID).build();
            return RECORD_ID;
        });

        when(mockHealthDataService.getAttachmentBuilder()).thenAnswer(
                invocation -> new DynamoHealthDataAttachment.Builder());

        when(mockHealthDataService.getRecordBuilder()).thenAnswer(invocation -> new DynamoHealthDataRecord.Builder());

        when(mockHealthDataService.getRecordById(RECORD_ID)).thenAnswer(invocation -> savedRecord);

        // set up UploadArtifactsHandler
        UploadArtifactsHandler uploadArtifactsHandler = new UploadArtifactsHandler();
        uploadArtifactsHandler.setHealthDataService(mockHealthDataService);
        uploadArtifactsHandler.setS3Helper(mockS3UploadHelper);

        // set up task factory
        List<UploadValidationHandler> handlerList = ImmutableList.of(s3DownloadHandler, decryptHandler, UNZIP_HANDLER,
                PARSE_JSON_HANDLER, iosSchemaValidationHandler, strictValidationHandler, transcribeConsentHandler,
                uploadArtifactsHandler);

        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(mockUploadDao);

        // create task, execute
        UploadValidationTask task = taskFactory.newTask(TestConstants.TEST_STUDY, UPLOAD);
        task.run();
    }

    private static void validateCommonRecordProps(HealthDataRecord record) {
        // Ignore ID - This one is created by the DAO, so it's not present in the passed in record.
        // Ignore version - That's internal.
        // Data, metadata, and schema fields are specific to individual tests.
        assertEquals(CREATED_ON_MILLIS, record.getCreatedOn().longValue());
        assertEquals(HEALTH_CODE, record.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, record.getStudyId());
        assertEquals(MOCK_TODAY, record.getUploadDate());
        assertEquals(UPLOAD_ID, record.getUploadId());
        assertEquals(MOCK_NOW_MILLIS, record.getUploadedOn().longValue());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, record.getUserSharingScope());
        assertEquals(EXTERNAL_ID, record.getUserExternalId());
        assertEquals(DATA_GROUP_SET, record.getUserDataGroups());
    }

    @Test
    public void survey() throws Exception {
        final String surveyCreatedOnStr = "2016-06-03T16:01:02.003-0700";
        final long surveyCreatedOnMillis = DateTime.parse(surveyCreatedOnStr).getMillis();

        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("AAA").withType(UploadFieldType.SINGLE_CHOICE)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("BBB").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("fencing", "football", "running", "swimming", "3").build(),
                new DynamoUploadFieldDefinition.Builder().withName("delicious").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("Yes", "No").withAllowOtherChoices(true).build(),
                new DynamoUploadFieldDefinition.Builder().withName("$sanitize..me")
                        .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("$dollar", "-dash", " space")
                        .build());

        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName("Survey Schema");
        schema.setRevision(2);
        schema.setSchemaId("survey-schema");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSurveyGuid("survey-guid");
        schema.setSurveyCreatedOn(surveyCreatedOnMillis);

        // set up survey (all we need is reference to the schema)
        DynamoSurvey survey = new DynamoSurvey();
        survey.setCreatedOn(surveyCreatedOnMillis);
        survey.setGuid("survey-guid");
        survey.setIdentifier("survey-schema");
        survey.setSchemaRevision(2);

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
                "   },{\n" +
                "       \"filename\":\"$sanitize..me.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"surveyGuid\":\"survey-guid\",\n" +
                "   \"surveyCreatedOn\":\"" + surveyCreatedOnStr + "\",\n" +
                "   \"appVersion\":\"version 1.0.0, build 1\",\n" +
                "   \"phoneInfo\":\"Unit Test Hardware\"\n" +
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

        String sanitizeMeJsonText = "{\n" +
                "   \"questionType\":0,\n" +
                "   \"choiceAnswers\":[\"$dollar\", \"-dash\", \" space\"],\n" +
                "   \"startDate\":\"2015-04-02T03:27:05-07:00\",\n" +
                "   \"questionTypeName\":\"MultipleChoice\",\n" +
                "   \"item\":\"$sanitize..me\",\n" +
                "   \"endDate\":\"2015-04-02T03:27:09-07:00\"\n" +
                "}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("AAA.json", aaaJsonText).put("BBB.json", bbbJsonText).put("delicious.json", deliciousJsonText)
                .put("$sanitize..me.json", sanitizeMeJsonText).build();

        // execute
        test(schema, survey, fileMap);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService, atLeastOnce()).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals("survey-schema", record.getSchemaId());
        assertEquals(2, record.getSchemaRevision());

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

        JsonNode sanitizeMeChoiceAnswersNode = dataNode.get("_sanitize.me");
        assertEquals(3, sanitizeMeChoiceAnswersNode.size());
        assertEquals("_dollar", sanitizeMeChoiceAnswersNode.get(0).textValue());
        assertEquals("_dash", sanitizeMeChoiceAnswersNode.get(1).textValue());
        assertEquals("_space", sanitizeMeChoiceAnswersNode.get(2).textValue());

        // validate no uploads to S3
        verifyZeroInteractions(mockS3UploadHelper);

        // verify no attachments
        verify(mockHealthDataService, never()).createOrUpdateAttachment(any(HealthDataAttachment.class));

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(UPLOAD, UploadStatus.SUCCEEDED, ImmutableList.of(), RECORD_ID);
    }

    @Test
    public void nonSurvey() throws Exception {
        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("CCC.txt").withType(UploadFieldType.ATTACHMENT_BLOB)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("DDD.csv").withType(UploadFieldType.ATTACHMENT_CSV)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("EEE.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("FFF.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_TABLE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("GGG.txt").withType(UploadFieldType.ATTACHMENT_V2)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.HHH")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.III").withType(UploadFieldType.BOOLEAN)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.JJJ")
                        .withType(UploadFieldType.CALENDAR_DATE).build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.LLL")
                        .withType(UploadFieldType.DURATION_V2).build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.MMM").withType(UploadFieldType.FLOAT)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.NNN")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.OOO").withType(UploadFieldType.INT)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.PPP").withType(UploadFieldType.STRING)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.QQQ").withType(UploadFieldType.TIME_V2)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.arrr")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new DynamoUploadFieldDefinition.Builder().withName("record.json.$sanitize..this..field")
                    .withType(UploadFieldType.STRING).build(),
                new DynamoUploadFieldDefinition.Builder().withName("$sanitize..blob..file")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new DynamoUploadFieldDefinition.Builder().withName("$sanitize..json..file.json")
                        .withType(UploadFieldType.ATTACHMENT_V2).build());

        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName("Non-Survey Schema");
        schema.setRevision(2);
        schema.setSchemaId("non-survey-schema");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        // set up upload files
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
                "       \"filename\":\"$sanitize..blob..file\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"$sanitize..json..file.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"item\":\"non-survey-schema\",\n" +
                "   \"schemaRevision\":2,\n" +
                "   \"appVersion\":\"version 1.0.0, build 1\",\n" +
                "   \"phoneInfo\":\"Unit Test Hardware\"\n" +
                "}";

        String cccTxtContent = "Blob file";

        String dddCsvContent = "foo,bar\nbaz,qux";

        String eeeJsonContent = "{\"key\":\"value\"}";

        String fffJsonContent = "[{\"name\":\"Dwayne\"},{\"name\":\"Eggplant\"}]";

        String gggTxtContent = "Arbitrary attachment";

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
                "   \"arrr\":\"2016-06-03T18:12:34.567+0900\",\n" +
                "   \"$sanitize..this..field\":\"$text..values..don't..sanitize\"\n" +
                "}";

        String sanitizeBlobFileContent = "#blob#file#not#sanitized..";

        String sanitizeJsonFileContent = "[\"  json!@not#$sanitized  \"]";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("CCC.txt", cccTxtContent).put("DDD.csv", dddCsvContent).put("EEE.json", eeeJsonContent)
                .put("FFF.json", fffJsonContent).put("GGG.txt", gggTxtContent).put("record.json", recordJsonContent)
                .put("$sanitize..blob..file", sanitizeBlobFileContent)
                .put("$sanitize..json..file.json", sanitizeJsonFileContent)
                .build();

        // execute
        test(schema, null, fileMap);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService, atLeastOnce()).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals("non-survey-schema", record.getSchemaId());
        assertEquals(2, record.getSchemaRevision());

        JsonNode dataNode = record.getData();
        assertEquals(18, dataNode.size());
        assertTrue(dataNode.get("record.json.III").booleanValue());
        assertEquals("2016-06-03", dataNode.get("record.json.JJJ").textValue());
        assertEquals("PT1H", dataNode.get("record.json.LLL").textValue());
        assertEquals(3.14, dataNode.get("record.json.MMM").doubleValue(), /*delta*/ 0.001);
        assertEquals(2, dataNode.get("record.json.OOO").intValue());
        assertEquals("1337", dataNode.get("record.json.PPP").textValue());
        assertEquals("19:21:35.378", dataNode.get("record.json.QQQ").textValue());
        assertEquals(DateTime.parse("2016-06-03T18:12:34.567+0900"),
                DateTime.parse(dataNode.get("record.json.arrr").textValue()));
        assertEquals("$text..values..don't..sanitize", dataNode.get("record.json._sanitize.this.field").textValue());

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
        JsonNode eeeJsonNode = getAttachmentAsJson(eeeJsonAttachmentId);
        assertEquals(1, eeeJsonNode.size());
        assertEquals("value", eeeJsonNode.get("key").textValue());

        String fffJsonAttachmentId = dataNode.get("FFF.json").textValue();
        JsonNode fffJsonNode = getAttachmentAsJson(fffJsonAttachmentId);
        assertEquals(2, fffJsonNode.size());

        assertEquals(1, fffJsonNode.get(0).size());
        assertEquals("Dwayne", fffJsonNode.get(0).get("name").textValue());

        assertEquals(1, fffJsonNode.get(1).size());
        assertEquals("Eggplant", fffJsonNode.get(1).get("name").textValue());

        String hhhAttachmentId = dataNode.get("record.json.HHH").textValue();
        JsonNode hhhNode = getAttachmentAsJson(hhhAttachmentId);
        assertEquals(3, hhhNode.size());
        assertEquals("attachment", hhhNode.get(0).textValue());
        assertEquals("inside", hhhNode.get(1).textValue());
        assertEquals("file", hhhNode.get(2).textValue());

        String sanitizeBlobFileAttachmentId = dataNode.get("_sanitize.blob.file").textValue();
        validateTextAttachment(sanitizeBlobFileContent, sanitizeBlobFileAttachmentId);

        String sanitizeJsonFileAttachmentId = dataNode.get("_sanitize.json.file.json").textValue();
        JsonNode sanitizeJsonNode = getAttachmentAsJson(sanitizeJsonFileAttachmentId);
        assertEquals(1, sanitizeJsonNode.size());
        assertEquals("  json!@not#$sanitized  ", sanitizeJsonNode.get(0).textValue());

        // verify attachments in HealthDataAttachments - Of all the attributes, the only one that actually matters is
        // the record ID
        ArgumentCaptor<HealthDataAttachment> attachmentCaptor = ArgumentCaptor.forClass(HealthDataAttachment.class);
        verify(mockHealthDataService, times(8)).createOrUpdateAttachment(attachmentCaptor.capture());
        for (HealthDataAttachment oneAttachment : attachmentCaptor.getAllValues()) {
            assertEquals(RECORD_ID, oneAttachment.getRecordId());
        }

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(UPLOAD, UploadStatus.SUCCEEDED, ImmutableList.of(), RECORD_ID);
    }

    private void validateTextAttachment(String expected, String attachmentId) throws Exception {
        ArgumentCaptor<byte[]> attachmentContentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockS3UploadHelper).writeBytesToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(attachmentId),
                attachmentContentCaptor.capture());
        assertEquals(expected, new String(attachmentContentCaptor.getValue(), Charsets.UTF_8));
    }

    private JsonNode getAttachmentAsJson(String attachmentId) throws Exception {
        ArgumentCaptor<byte[]> attachmentContentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockS3UploadHelper).writeBytesToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(attachmentId),
                attachmentContentCaptor.capture());
        return BridgeObjectMapper.get().readTree(attachmentContentCaptor.getValue());
    }

    @Test
    public void missingOptionalFieldAboveMaxAppVersion() throws Exception {
        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("record.json.value")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).withMaxAppVersion(20).build());

        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName("Max App Version Test");
        schema.setRevision(2);
        schema.setSchemaId("max-app-version-test");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        // set up upload files
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"record.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"item\":\"max-app-version-test\",\n" +
                "   \"schemaRevision\":2,\n" +
                "   \"appVersion\":\"version 1.30, build 30\",\n" +
                "   \"phoneInfo\":\"Unit Test Hardware\"\n" +
                "}";

        String recordJsonText = "{}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("record.json", recordJsonText).build();

        // execute
        test(schema, null, fileMap);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService, atLeastOnce()).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals("max-app-version-test", record.getSchemaId());
        assertEquals(2, record.getSchemaRevision());

        JsonNode dataNode = record.getData();
        assertEquals(0, dataNode.size());

        // validate no uploads to S3
        verifyZeroInteractions(mockS3UploadHelper);

        // verify no attachments
        verify(mockHealthDataService, never()).createOrUpdateAttachment(any(HealthDataAttachment.class));

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(UPLOAD, UploadStatus.SUCCEEDED, ImmutableList.of(), RECORD_ID);
    }

    @Test
    public void missingOptionalFieldBelowMaxAppVersion() throws Exception {
        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("record.json.value")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).withMaxAppVersion(20).build());

        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName("Max App Version Test");
        schema.setRevision(2);
        schema.setSchemaId("max-app-version-test");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        // set up upload files
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"record.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"item\":\"max-app-version-test\",\n" +
                "   \"schemaRevision\":2,\n" +
                "   \"appVersion\":\"version 1.10, build 10\",\n" +
                "   \"phoneInfo\":\"Unit Test Hardware\"\n" +
                "}";

        String recordJsonText = "{}";

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("record.json", recordJsonText).build();

        // execute
        test(schema, null, fileMap);

        // no records or attachments are created; nothing is uploaded to S3
        verifyZeroInteractions(mockS3UploadHelper);
        verify(mockHealthDataService, never()).createOrUpdateRecord(any(HealthDataRecord.class));
        verify(mockHealthDataService, never()).createOrUpdateAttachment(any(HealthDataAttachment.class));

        // verify upload dao write validation status
        // Error message list may contain other messages. For simplicity, concat them all together and just search for
        // the specific one you're looking for.
        // Additionally, record ID was never assigned, since upload failed out at the StrictValidationHandler, long
        // before the UploadArtifactsHandler.
        ArgumentCaptor<List> errorMessageListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockUploadDao).writeValidationStatus(eq(UPLOAD), eq(UploadStatus.VALIDATION_FAILED),
                errorMessageListCaptor.capture(), isNull(String.class));
        String joinedErrorMessageList = BridgeUtils.COMMA_JOINER.join(errorMessageListCaptor.getValue());
        assertTrue(joinedErrorMessageList.contains("Required field record.json.value missing"));
    }
}
