package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.notNull;
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
import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadValidationTaskTest {
    private static final long CREATED_ON = 1424136378727L;
    private static final String HEALTH_CODE = TestUtils.randomName(UploadValidationTaskTest.class);
    private static final String RECORD_ID = TestUtils.randomName(UploadValidationTaskTest.class);
    private static final String RECORD_ID_2 = TestUtils.randomName(UploadValidationTaskTest.class);
    private static final String RECORD_ID_3 = TestUtils.randomName(UploadValidationTaskTest.class);
    private static final String DATA_TEXT = "{\"data\":\"dummy value\"}";
    private static final String METADATA_TEXT = "{\"metadata\":\"dummy meta value\"}";
    private static final String SCHEMA_ID = TestUtils.randomName(UploadValidationTaskTest.class);
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

    private UploadValidationContext ctx;
    private HealthDataService healthDataService;
    private InMemoryFileHelper inMemoryFileHelper;
    private UploadDao mockDao;
    private UploadValidationTask task;
    private Upload upload;

    @Before
    public void setup() throws IOException {
        // Mock health data service
        testRecord = makeRecordWithId(RECORD_ID);
        HealthDataRecord testRecordDupe = makeRecordWithId(RECORD_ID_2);
        HealthDataRecord testRecordDupe2 = makeRecordWithId(RECORD_ID_3);

        List<HealthDataRecord> testRecordDupeListNormal = ImmutableList.of(testRecord, testRecordDupe);
        testRecordDupeListMulti = ImmutableList.of(testRecord, testRecordDupe, testRecordDupe2);

        healthDataService = mock(HealthDataService.class);
        when(healthDataService.getRecordById(eq(RECORD_ID))).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), anyLong(), any())).thenReturn(
                testRecordDupeListNormal);

        // Set up context
        Study study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        upload = Upload.create();
        upload.setUploadId("test-upload");

        ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload);

        // Set up other pre-reqs
        inMemoryFileHelper = new InMemoryFileHelper();
        mockDao = mock(UploadDao.class);

        // Set up task. Spy so we can verify some calls.
        task = spy(new UploadValidationTask(ctx));
        task.setFileHelper(inMemoryFileHelper);
        task.setHandlerList(handlerList);
        task.setHealthDataService(healthDataService);
        task.setUploadDao(mockDao);
    }

    @Test
    public void happyCase() {
        // test handlers

        // execute
        testHelper(handlerList, UploadStatus.SUCCEEDED, RECORD_ID);
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
        doThrow(exClass).when(barHandler).handle(notNull());

        UploadValidationHandler bazHandler = mock(UploadValidationHandler.class);
        verifyZeroInteractions(bazHandler);

        UploadValidationHandler recordIdHandler = new RecordIdHandler("never called");

        List<UploadValidationHandler> handlerList = ImmutableList.of(fooHandler, barHandler, bazHandler,
                recordIdHandler);

        // execute
        testHelper(handlerList, UploadStatus.VALIDATION_FAILED, null);
        assertFalse(ctx.getSuccess());

        // Validate validation messages. First message is foo handler. Second message is error message. Just check that
        // the second message exists.
        List<String> messageList = ctx.getMessageList();
        assertEquals(2, messageList.size());
        assertEquals("foo succeeded", messageList.get(0));
        assertFalse(Strings.isNullOrEmpty(messageList.get(1)));
    }

    // helper test method, encapsulating core setup and validation
    private void testHelper(List<UploadValidationHandler> handlerList,
            UploadStatus expectedStatus, String expectedRecordId) {
        // Test might have a custom handler list
        if (handlerList != null) {
            task.setHandlerList(handlerList);
        }

        // execute
        task.run();

        // validate the upload dao write validation status call
        verify(mockDao).writeValidationStatus(upload, expectedStatus, ctx.getMessageList(), expectedRecordId);

        // Validate that we clean up the temp directory.
        assertTrue(inMemoryFileHelper.isEmpty());
    }

    @Test
    public void writeValidationStatusException() {
        // Trivial record ID handler, to make the test not degenerate.
        List<UploadValidationHandler> handlerList = ImmutableList.of(new RecordIdHandler("will fail"));

        // mock dao
        RuntimeException toThrow = new RuntimeException();
        doThrow(toThrow).when(mockDao).writeValidationStatus(upload, UploadStatus.SUCCEEDED, ImmutableList.of(),
                "will fail");

        // set up validation task
        task.setHandlerList(handlerList);

        // execute
        task.run();

        // verify log helper was called
        verify(task).logWriteValidationStatusException(UploadStatus.SUCCEEDED, toThrow);
    }

    @Test
    public void dedupeNullRecordId() {
        // set up validation task
        task.setHandlerList(nullRecordIdHandlerList);

        // execute
        task.run();

        // should have no interaction with dedupe logic
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
        verify(task, times(0)).logErrorMsg(any());
    }

    @Test
    public void dedupeInformation() {
        // execute
        task.run();

        // verify log helper was called
        verify(task).logDuplicateUploadRecords(eq(testRecord), eq(ImmutableList.of(RECORD_ID_2)));
    }

    @Test
    public void dedupeWithoutDuplicate() {
        // only return one test record
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), anyLong(), any())).thenReturn(
                ImmutableList.of(testRecord));

        // execute
        task.run();

        // verify log helper was NOT called
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeNullRecord() {
        // return a null record
        when(healthDataService.getRecordById(any())).thenReturn(null);
        task.run();
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeEmptyList() {
        // return an empty list
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(any(), anyLong(), any())).thenReturn(
                ImmutableList.of());
        task.run();
        verify(task, times(0)).logDuplicateUploadRecords(any(), any());
    }

    @Test
    public void dedupeMultipleDuplicates() {
        when(healthDataService.getRecordById(any())).thenReturn(testRecord);
        when(healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(eq(HEALTH_CODE), eq(CREATED_ON), eq(SCHEMA_ID))).thenReturn(testRecordDupeListMulti);

        task.run();
        verify(task).logDuplicateUploadRecords(eq(testRecord), eq(ImmutableList.of(RECORD_ID_2, RECORD_ID_3)));
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

    private static HealthDataRecord makeRecordWithId(String recordId) throws IOException {
        HealthDataRecord record = HealthDataRecord.create();
        record.setCreatedOn(CREATED_ON);
        record.setHealthCode(HEALTH_CODE);
        record.setId(recordId);
        record.setSchemaId(SCHEMA_ID);
        record.setSchemaRevision(SCHEMA_REV);
        record.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        record.setUploadDate(UPLOAD_DATE);
        record.setUploadedOn(UPLOADED_ON);
        record.setUploadId(UPLOAD_ID);
        record.setUserSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
        record.setUserExternalId(USER_EXTERNAL_ID);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setData(BridgeObjectMapper.get().readTree(DATA_TEXT));
        record.setMetadata(BridgeObjectMapper.get().readTree(METADATA_TEXT));
        return record;
    }
}
