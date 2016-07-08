package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;

@SuppressWarnings("unchecked")
public class UploadServiceMockTest {
    @Test(expected = BadRequestException.class)
    public void getNullUploadId() {
        new UploadService().getUpload(null);
    }

    @Test(expected = BadRequestException.class)
    public void getEmptyUploadId() {
        new UploadService().getUpload("");
    }

    @Test
    public void getUpload() {
        // This is a simple call through to the DAO. Test the data flow.

        // mock upload dao
        UploadDao mockDao = mock(UploadDao.class);
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getUpload");
        when(mockDao.getUpload("test-upload-id")).thenReturn(mockUpload);

        // create test service with mock
        UploadService svc = new UploadService();
        svc.setUploadDao(mockDao);

        // execute and validate
        Upload retVal = svc.getUpload("test-upload-id");
        assertSame(mockUpload, retVal);
    }

    @Test(expected = BadRequestException.class)
    public void getStatusNullUploadId() {
        new UploadService().getUploadValidationStatus(null);
    }

    @Test(expected = BadRequestException.class)
    public void getStatusEmptyUploadId() {
        new UploadService().getUploadValidationStatus("");
    }

    @Test
    public void getStatus() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatus");
        mockUpload.setStatus(UploadStatus.VALIDATION_FAILED);
        mockUpload.setUploadId("no-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatus - message"));

        UploadDao mockDao = mock(UploadDao.class);
        when(mockDao.getUpload("no-record-id")).thenReturn(mockUpload);

        // create test service with mock
        UploadService svc = new UploadService();
        svc.setUploadDao(mockDao);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("no-record-id");
        assertEquals("no-record-id", status.getId());
        assertEquals(UploadStatus.VALIDATION_FAILED, status.getStatus());
        assertNull(status.getRecord());

        assertEquals(1, status.getMessageList().size());
        assertEquals("getStatus - message", status.getMessageList().get(0));
    }

    @Test
    public void getStatusWithRecord() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatusWithRecord");
        mockUpload.setRecordId("test-record-id");
        mockUpload.setStatus(UploadStatus.SUCCEEDED);
        mockUpload.setUploadId("with-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatusWithRecord - message"));

        UploadDao mockDao = mock(UploadDao.class);
        when(mockDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        DynamoHealthDataRecord dummyRecord = new DynamoHealthDataRecord();
        HealthDataService mockHealthDataService = mock(HealthDataService.class);
        when(mockHealthDataService.getRecordById("test-record-id")).thenReturn(dummyRecord);

        // create test service with mocks
        UploadService svc = new UploadService();
        svc.setHealthDataService(mockHealthDataService);
        svc.setUploadDao(mockDao);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("with-record-id");
        assertEquals("with-record-id", status.getId());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());
        assertSame(dummyRecord, status.getRecord());

        assertEquals(1, status.getMessageList().size());
        assertEquals("getStatusWithRecord - message", status.getMessageList().get(0));
    }

    // branch coverage
    @Test
    public void getStatusRecordIdWithNoRecord() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatusRecordIdWithNoRecord");
        mockUpload.setRecordId("missing-record-id");
        mockUpload.setStatus(UploadStatus.SUCCEEDED);
        mockUpload.setUploadId("with-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatusRecordIdWithNoRecord - message"));

        UploadDao mockDao = mock(UploadDao.class);
        when(mockDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        HealthDataService mockHealthDataService = mock(HealthDataService.class);
        when(mockHealthDataService.getRecordById("missing-record-id")).thenThrow(IllegalArgumentException.class);

        // create test service with mocks
        UploadService svc = new UploadService();
        svc.setHealthDataService(mockHealthDataService);
        svc.setUploadDao(mockDao);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("with-record-id");
        assertEquals("with-record-id", status.getId());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());
        assertNull(status.getRecord());

        assertEquals(1, status.getMessageList().size());
        assertEquals("getStatusRecordIdWithNoRecord - message", status.getMessageList().get(0));
    }
}
