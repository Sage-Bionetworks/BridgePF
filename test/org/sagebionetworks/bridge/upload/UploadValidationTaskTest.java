package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDaoDdbTest;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadValidationTaskTest {
    private static final long CREATED_ON = 1424136378727L;
    private static final String HEALTH_CODE = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String RECORD_ID = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String RECORD_ID_2 = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String RECORD_ID_3 = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final String DATA_TEXT = "{\"data\":\"dummy value\"}";
    private static final String METADATA_TEXT = "{\"metadata\":\"dummy meta value\"}";
    private static final String SCHEMA_ID = TestUtils.randomName(DynamoHealthDataDaoDdbTest.class);
    private static final int SCHEMA_REV = 2;
    private static final LocalDate UPLOAD_DATE = LocalDate.parse("2016-05-06");
    private static final String UPLOAD_ID = "upload-id";
    private static final long UPLOADED_ON = 1462575525894L;
    private static final String USER_EXTERNAL_ID = "external-id";

    private HealthDataRecord testRecord;

    private List<HealthDataRecord> testRecordDupeListMulti;

    private final List<UploadValidationHandler> handlerList = ImmutableList.of(
            new MessageHandler("foo was here"), new MessageHandler("bar was here"),
            new MessageHandler("kilroy was here"), new RecordIdHandler(RECORD_ID));

    private final List<UploadValidationHandler> nullRecordIdHandlerList = ImmutableList.of(
            new MessageHandler("foo was here"), new MessageHandler("bar was here"),
            new MessageHandler("kilroy was here"), new RecordIdHandler(null));


    private HealthDataService healthDataService;

    @Before
    public void setup() throws IOException {
        testRecord = new DynamoHealthDataRecord.Builder()
                .withCreatedOn(CREATED_ON)
                .withHealthCode(HEALTH_CODE)
                .withId(RECORD_ID)
                .withSchemaId(SCHEMA_ID)
                .withSchemaRevision(SCHEMA_REV)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withUploadDate(UPLOAD_DATE)
                .withUploadedOn(UPLOADED_ON)
                .withUploadId(UPLOAD_ID)
                .withUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS)
                .withUserExternalId(USER_EXTERNAL_ID)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withData(BridgeObjectMapper.get().readTree(DATA_TEXT))
                .withMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT))
                .build();

        HealthDataRecord testRecordDupe = new DynamoHealthDataRecord.Builder()
                .withCreatedOn(CREATED_ON)
                .withHealthCode(HEALTH_CODE)
                .withId(RECORD_ID_2)
                .withSchemaId(SCHEMA_ID)
                .withSchemaRevision(SCHEMA_REV)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withUploadDate(UPLOAD_DATE)
                .withUploadedOn(UPLOADED_ON)
                .withUploadId(UPLOAD_ID)
                .withUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS)
                .withUserExternalId(USER_EXTERNAL_ID)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withData(BridgeObjectMapper.get().readTree(DATA_TEXT))
                .withMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT))
                .build();

        HealthDataRecord testRecordDupe2 = new DynamoHealthDataRecord.Builder()
                .withCreatedOn(CREATED_ON)
                .withHealthCode(HEALTH_CODE)
                .withId(RECORD_ID_3)
                .withSchemaId(SCHEMA_ID)
                .withSchemaRevision(SCHEMA_REV)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withUploadDate(UPLOAD_DATE)
                .withUploadedOn(UPLOADED_ON)
                .withUploadId(UPLOAD_ID)
                .withUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS)
                .withUserExternalId(USER_EXTERNAL_ID)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withData(BridgeObjectMapper.get().readTree(DATA_TEXT))
                .withMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT))
                .build();

        List<HealthDataRecord> testRecordDupeListNormal = ImmutableList.of(testRecord, testRecordDupe);
        testRecordDupeListMulti = ImmutableList.of(testRecord, testRecordDupe, testRecordDupe2);

        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(eq(RECORD_ID))).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), any(), any())).thenReturn(
                testRecordDupeListNormal);
    }

    @Test
    public void happyCase() {
        // test handlers

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.SUCCEEDED, RECORD_ID);
        assertTrue(ctx.getSuccess());

        // validate that the handlers ran by checking the messages they wrote
        List<String> messageList = ctx.getMessageList();
        assertEquals(3, messageList.size());
        assertEquals("foo was here", messageList.get(0));
        assertEquals("bar was here", messageList.get(1));
        assertEquals("kilroy was here", messageList.get(2));
    }

    @Test
    public void uploadValidationException() throws Exception {
        testExceptionHelper(UploadValidationException.class);
    }

    @Test
    public void runtimeException() throws Exception {
        testExceptionHelper(RuntimeException.class);
    }

    @Test
    public void error() throws Exception {
        testExceptionHelper(OutOfMemoryError.class);
    }

    // helper test method for the exception tests
    private void testExceptionHelper(Class<? extends Throwable> exClass) throws Exception {
        // test handlers
        UploadValidationHandler fooHandler = new MessageHandler("foo succeeded");

        UploadValidationHandler barHandler = mock(UploadValidationHandler.class);
        doThrow(exClass).when(barHandler).handle(notNull(UploadValidationContext.class));

        UploadValidationHandler bazHandler = mock(UploadValidationHandler.class);
        verifyZeroInteractions(bazHandler);

        UploadValidationHandler recordIdHandler = new RecordIdHandler("never called");

        List<UploadValidationHandler> handlerList = ImmutableList.of(fooHandler, barHandler, bazHandler,
                recordIdHandler);

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.VALIDATION_FAILED, null);
        assertFalse(ctx.getSuccess());

        // Validate validation messages. First message is foo handler. Second message is error message. Just check that
        // the second message exists.
        List<String> messageList = ctx.getMessageList();
        assertEquals(2, messageList.size());
        assertEquals("foo succeeded", messageList.get(0));
        assertFalse(Strings.isNullOrEmpty(messageList.get(1)));
    }

    // helper test method, encapsulating core setup and validation
    private UploadValidationContext testHelper(List<UploadValidationHandler> handlerList,
            UploadStatus expectedStatus, String expectedRecordId) {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // mock dao
        UploadDao mockDao = mock(UploadDao.class);

        // set up validation task
        UploadValidationTask task = new UploadValidationTask(ctx);
        task.setHandlerList(handlerList);
        task.setUploadDao(mockDao);
        task.setHealthDataService(healthDataService);

        // execute
        task.run();

        // validate the upload dao write validation status call
        verify(mockDao).writeValidationStatus(upload2, expectedStatus, ctx.getMessageList(), expectedRecordId);

        return ctx;
    }

    @Test
    public void writeValidationStatusException() {
        // Trivial record ID handler, to make the test not degenerate.
        List<UploadValidationHandler> handlerList = ImmutableList.of(new RecordIdHandler("will fail"));

        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // mock dao
        UploadDao mockDao = mock(UploadDao.class);
        RuntimeException toThrow = new RuntimeException();
        doThrow(toThrow).when(mockDao).writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of(),
                "will fail");

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);
        task.setUploadDao(mockDao);
        task.setHealthDataService(healthDataService);

        // execute
        task.run();

        // verify log helper was called
        verify(task).logWriteValidationStatusException(UploadStatus.SUCCEEDED, toThrow);
    }

    @Test
    public void dedupeNullRecordId() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(nullRecordIdHandlerList);
        task.setHealthDataService(healthDataService);

        // execute
        task.run();

        // should have no interaction with dedupe logic
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
        verify(task, times(0)).logErrorMsg(any());
    }

    @Test
    public void dedupeInformation() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);
        task.setHealthDataService(healthDataService);

        // execute
        task.run();

        // verify log helper was called
        verify(task).logDuplicateUploadRecords(eq(testRecord), eq(ImmutableList.of(RECORD_ID_2)));
    }

    @Test
    public void dedupeWithoutDuplicate() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);
        // only return one test record
        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), any(), any())).thenReturn(
                ImmutableList.of(testRecord));
        task.setHealthDataService(healthDataService);

        // execute
        task.run();

        // verify log helper was NOT called
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeNullRecord() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);
        // return a null record
        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(any())).thenReturn(null);
        task.setHealthDataService(healthDataService);
        task.run();
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeEmptyList() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);
        // return an empty list
        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), any(), any())).thenReturn(
                ImmutableList.of());
        task.setHealthDataService(healthDataService);
        task.run();
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeMultipleDuplicates() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);

        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(eq(HEALTH_CODE), eq(CREATED_ON), eq(SCHEMA_ID))).thenReturn(testRecordDupeListMulti);
        task.setHealthDataService(healthDataService);

        task.run();
        verify(task).logDuplicateUploadRecords(eq(testRecord), eq(ImmutableList.of(RECORD_ID_2, RECORD_ID_3)));
    }

    @Test
    public void dedupeMaxDupes() {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // set up validation task
        UploadValidationTask task = spy(new UploadValidationTask(ctx));
        task.setHandlerList(handlerList);

        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);

        // Create a list with 1 original and 11 dupes. Logging will max out at 10 dupes.
        List<HealthDataRecord> dupeRecordList = new ArrayList<>();
        dupeRecordList.add(testRecord);

        for (int i = 0; i < 12; i++) {
            // All we care about is record ID. Bypass the builder, so we don't have to validate and get stuck with
            // quadratic updates when we make changes.
            DynamoHealthDataRecord dupeRecord = new DynamoHealthDataRecord();
            dupeRecord.setId(RECORD_ID + "-" + i);
            dupeRecordList.add(dupeRecord);
        }

        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(eq(HEALTH_CODE), eq(CREATED_ON), eq(SCHEMA_ID)))
                .thenReturn(dupeRecordList);
        task.setHealthDataService(healthDataService);

        // execute and verify
        task.run();

        List<String> expectedDupeRecordIdList = new ArrayList<>();
        for (int i = 0; i < BridgeConstants.DUPE_RECORDS_MAX_COUNT; i++) {
            expectedDupeRecordIdList.add(RECORD_ID + "-" + i);
        }

        verify(task).logDuplicateUploadRecords(testRecord, expectedDupeRecordIdList);
    }

    // Test handler that makes its presence known only by writing a message to the validation context.
    private static class MessageHandler implements UploadValidationHandler {
        private final String message;

        public MessageHandler(String message) {
            this.message = message;
        }

        @Override
        public void handle(@Nonnull UploadValidationContext context) {
            context.addMessage(message);
        }
    }

    // Test handler that simulates writing the record ID to the context, so we can test writing the record ID to the
    // validation status.
    private static class RecordIdHandler implements UploadValidationHandler {
        private final String recordId;

        public RecordIdHandler(String recordId) {
            this.recordId = recordId;
        }

        @Override
        public void handle(@Nonnull UploadValidationContext context) {
            context.setRecordId(recordId);
        }
    }
}
