package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class UploadServiceMockTest {
    
    private static final DateTime START_TIME = DateTime.parse("2016-04-02T10:00:00.000Z");
    private static final DateTime END_TIME = DateTime.parse("2016-04-03T10:00:00.000Z");
    
    @Mock
    private UploadDao mockDao;
    
    @Mock
    private HealthDataService mockHealthDataService;
    
    @Mock
    private Upload mockUpload;
    
    @Mock
    private UploadValidationStatus mockStatus;
    
    @Mock
    private HealthDataRecord mockRecord;
    
    @Mock
    private Upload mockFailedUpload;

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
    
    private void setupUploadMocks() {
        // Mock upload
        doReturn("upload-id").when(mockUpload).getUploadId();
        doReturn(UploadStatus.SUCCEEDED).when(mockUpload).getStatus();
        doReturn("record-id").when(mockUpload).getRecordId();
        
        // Failed mock upload
        doReturn("failed-upload-id").when(mockFailedUpload).getUploadId();
        doReturn(UploadStatus.REQUESTED).when(mockFailedUpload).getStatus();
        
        // Mock getUploads/getUpload calls
        List<? extends Upload> results = Lists.newArrayList(mockUpload, mockFailedUpload);
        doReturn(results).when(mockDao).getUploads("ABC", START_TIME, END_TIME);
        doReturn(results).when(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME);
        doReturn(mockUpload).when(mockDao).getUpload("upload-id");
        doReturn(mockFailedUpload).when(mockDao).getUpload("failed-upload-id");
        
        // Mock the record returned from the validation status record
        doReturn("schema-id").when(mockRecord).getSchemaId();
        doReturn(10).when(mockRecord).getSchemaRevision();
        // Mock UploadValidationStatus from health data record;
        doReturn(mockRecord).when(mockHealthDataService).getRecordById("record-id");
    }
    
    // Mock a successful and unsuccessful upload. The successful upload should call to get information 
    // from the health data record table (schema id/revision). All should be merged correctly in the 
    // resulting views.
    @Test
    public void canGetUploads() throws Exception {
        setupUploadMocks();
        DateTimeRangeResourceList<? extends UploadView> returned = svc.getUploads("ABC", START_TIME, END_TIME);
        
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME);
        verify(mockHealthDataService).getRecordById("record-id");
        verifyNoMoreInteractions(mockHealthDataService);
        
        // The two sources of information are combined in the view.
        UploadView view = returned.getItems().get(0);
        assertEquals(UploadStatus.SUCCEEDED, view.getUpload().getStatus());
        assertEquals("schema-id", view.getSchemaId());
        assertEquals(new Integer(10), view.getSchemaRevision());
        
        UploadView failedView = returned.getItems().get(1);
        assertEquals(UploadStatus.REQUESTED, failedView.getUpload().getStatus());
        assertNull(failedView.getSchemaId());
        assertNull(failedView.getSchemaRevision());
    }
    
    @Test
    public void canGetStudyUploads() throws Exception {
        setupUploadMocks();
        
        // Now verify the study uploads works
        DateTimeRangeResourceList<? extends UploadView> returned = svc.getStudyUploads(TestConstants.TEST_STUDY,
                START_TIME, END_TIME);
        
        verify(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME);
        verify(mockHealthDataService).getRecordById("record-id");
        verifyNoMoreInteractions(mockHealthDataService);
        
        // The two sources of information are combined in the view.
        UploadView view = returned.getItems().get(0);
        assertEquals(UploadStatus.SUCCEEDED, view.getUpload().getStatus());
        // Does not have schema information for this view of uploads.
        assertEquals("schema-id", view.getSchemaId());
        assertEquals(new Integer(10), view.getSchemaRevision());
        
        UploadView failedView = returned.getItems().get(1);
        assertEquals(UploadStatus.REQUESTED, failedView.getUpload().getStatus());
        assertNull(failedView.getSchemaId());
        assertNull(failedView.getSchemaRevision());
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
    
    @Test
    public void deleteUploadsByHealthCodeWorks() {
        svc.deleteUploadsForHealthCode("ABC");
        verify(mockDao).deleteUploadsForHealthCode("ABC");
    }
    
    @Test
    public void deleteUploadsByHealthCodeRequiresHealthCode() {
        try {
            svc.deleteUploadsForHealthCode("");
            fail("Should have thrown exception");
        } catch(IllegalArgumentException e) {
            // expected
        }
        verify(mockDao, never()).deleteUploadsForHealthCode(any());
    }
}
