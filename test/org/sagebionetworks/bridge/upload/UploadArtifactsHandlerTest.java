package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadArtifactsHandlerTest {
    private static final String TEST_UPLOAD_ID = "test-upload";

    private HealthDataService mockHealthDataService;
    private UploadArtifactsHandler handler;

    @Before
    public void setup() {
        // Mock health data service.
        mockHealthDataService = mock(HealthDataService.class);
        when(mockHealthDataService.createOrUpdateRecord(any()))
            .thenAnswer(invocation -> ((HealthDataRecord)invocation.getArgument(0)).getId());

        // Set up handler.
        handler = new UploadArtifactsHandler();
        handler.setHealthDataService(mockHealthDataService);
    }

    @Test
    public void createNewRecord() {
        test();
    }

    @Test
    public void updateExistingRecord() {
        // Mock health data service to return existing record. The only values that matter are version.
        HealthDataRecord existingRecord = HealthDataRecord.create();
        existingRecord.setId(TEST_UPLOAD_ID);
        existingRecord.setVersion(42L);

        when(mockHealthDataService.getRecordById(TEST_UPLOAD_ID)).thenReturn(existingRecord);

        // Execute test.
        test();

        // Verify version was set properly.
        ArgumentCaptor<HealthDataRecord> createdRecordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(createdRecordCaptor.capture());

        HealthDataRecord createdRecord = createdRecordCaptor.getValue();
        assertEquals(42L, createdRecord.getVersion().longValue());
    }

    private void test() {
        // Make record. This test handles records almost entirely opaquely, so for the purposes of this test, a blank
        // record will suffice.
        HealthDataRecord record = HealthDataRecord.create();

        // only need upload ID from upload
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);

        // set up context
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthDataRecord(record);
        context.setUpload(upload);

        // execute
        handler.handle(context);

        // Validate result. Record ID equal to upload ID is the most important. The rest are just copied.
        ArgumentCaptor<HealthDataRecord> createdRecordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(createdRecordCaptor.capture());

        HealthDataRecord createdRecord = createdRecordCaptor.getValue();
        assertEquals(TEST_UPLOAD_ID, createdRecord.getId());
        assertSame(record, createdRecord);

        // validate record ID in the context
        assertEquals(TEST_UPLOAD_ID, context.getRecordId());

        // validate no messages on the context
        assertTrue(context.getMessageList().isEmpty());
    }
}
