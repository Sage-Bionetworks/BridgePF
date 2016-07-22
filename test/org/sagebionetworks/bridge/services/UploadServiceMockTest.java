package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class UploadServiceMockTest {
    
    private static final DateTime START_TIME = DateTime.parse("2016-04-02T10:00:00.000Z");
    private static final DateTime END_TIME = DateTime.parse("2016-04-03T10:00:00.000Z");
    
    @Mock
    private UploadDao mockDao;
    
    @Mock
    private HealthDataService mockHealthDataService;
    
    private UploadService svc;
    
    @Before
    public void before() {
        svc = new UploadService();
        svc.setUploadDao(mockDao);
        svc.setHealthDataService(mockHealthDataService);
    }
    
    @Test(expected = BadRequestException.class)
    public void getNullUploadId() {
        svc.getUpload(null);
    }

    @Test(expected = BadRequestException.class)
    public void getEmptyUploadId() {
        svc.getUpload("");
    }

    @Test
    public void getUpload() {
        // This is a simple call through to the DAO. Test the data flow.

        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getUpload");
        when(mockDao.getUpload("test-upload-id")).thenReturn(mockUpload);

        // execute and validate
        Upload retVal = svc.getUpload("test-upload-id");
        assertSame(mockUpload, retVal);
    }

    @Test(expected = BadRequestException.class)
    public void getStatusNullUploadId() {
        svc.getUploadValidationStatus(null);
    }

    @Test(expected = BadRequestException.class)
    public void getStatusEmptyUploadId() {
        svc.getUploadValidationStatus("");
    }

    @Test
    public void getStatus() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatus");
        mockUpload.setStatus(UploadStatus.VALIDATION_FAILED);
        mockUpload.setUploadId("no-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatus - message"));

        when(mockDao.getUpload("no-record-id")).thenReturn(mockUpload);

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

        when(mockDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        DynamoHealthDataRecord dummyRecord = new DynamoHealthDataRecord();
        when(mockHealthDataService.getRecordById("test-record-id")).thenReturn(dummyRecord);

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

        when(mockDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        when(mockHealthDataService.getRecordById("missing-record-id")).thenThrow(IllegalArgumentException.class);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("with-record-id");
        assertEquals("with-record-id", status.getId());
        assertEquals(UploadStatus.SUCCEEDED, status.getStatus());
        assertNull(status.getRecord());

        assertEquals(1, status.getMessageList().size());
        assertEquals("getStatusRecordIdWithNoRecord - message", status.getMessageList().get(0));
    }
    
    @Test
    public void canGetUploads() {
        svc.getUploads("ABC", START_TIME, END_TIME);
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME);
    }
    
    @Test
    public void canPassStartTimeOnly() {
        svc.getUploads("ABC", START_TIME, null);
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME);
    }
    
    @Test
    public void canPassEndTimeOnly() {
        svc.getUploads("ABC", null, END_TIME);
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME);
    }
    
    @Test
    public void canPassNoTimes() {
        ArgumentCaptor<DateTime> start = ArgumentCaptor.forClass(DateTime.class);
        ArgumentCaptor<DateTime> end = ArgumentCaptor.forClass(DateTime.class);
        
        svc.getUploads("ABC", null, null);
        
        verify(mockDao).getUploads(eq("ABC"), start.capture(), end.capture());
        
        DateTime actualStart = start.getValue();
        DateTime actualEnd = end.getValue();
        assertEquals(actualStart.plusDays(1), actualEnd);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifiesEndTimeNotBeforeStartTime() {
        svc.getUploads("ABC", END_TIME, START_TIME);
    }

    @Test(expected = BadRequestException.class)
    public void verifiesTimeRange() {
        svc.getUploads("ABC", START_TIME.minusDays(1).minusMinutes(1), END_TIME);
    }
}
