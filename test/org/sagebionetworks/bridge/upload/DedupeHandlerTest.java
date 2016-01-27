package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDedupeDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.schema.UploadSchemaKey;

@SuppressWarnings("unchecked")
public class DedupeHandlerTest {
    private static final UploadSchemaKey TEST_SCHEMA_KEY = new UploadSchemaKey.Builder().withStudyId("test-study")
            .withSchemaId("test-schema").withRevision(7).build();

    private UploadValidationContext context;
    private DedupeHandler handler;
    private DynamoUploadDedupeDao mockDao;

    @Before
    public void before() {
        // set up handler and mock dao
        mockDao = mock(DynamoUploadDedupeDao.class);
        handler = new DedupeHandler();
        handler.setUploadDedupeDao(mockDao);

        // Set up context. We need specifically upload ID from Upload; and health code, created on, and schema from
        // HealthRecordBuilder.
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId("test-upload");

        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder().withCreatedOn(42000L)
                .withHealthCode("test-health-code").withStudyId("test-study").withSchemaId("test-schema")
                .withSchemaRevision(7);

        context = new UploadValidationContext();
        context.setUpload(upload);
        context.setHealthDataRecordBuilder(recordBuilder);
    }

    @Test
    public void isDupe() {
        // execute and validate - In this case, if it's a dupe, nothing happens, since we're in "log and observe" mode
        when(mockDao.isDuplicate(42000L, "test-health-code", TEST_SCHEMA_KEY)).thenReturn(true);
        handler.handle(context);
    }

    @Test
    public void isNotDupe() {
        // Because this is not a dupe, we also need to register the upload.
        when(mockDao.isDuplicate(42000L, "test-health-code", TEST_SCHEMA_KEY)).thenReturn(false);
        handler.handle(context);
        verify(mockDao).registerUpload(42000L, "test-health-code", TEST_SCHEMA_KEY, "test-upload");
    }

    @Test
    public void error() {
        // We're in "log and observe" mode, so throwing an exception means we log it and swallow it.
        when(mockDao.isDuplicate(42000L, "test-health-code", TEST_SCHEMA_KEY)).thenThrow(RuntimeException.class);
        handler.handle(context);
    }
}
