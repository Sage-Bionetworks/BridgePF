package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;

@SuppressWarnings("ConstantConditions")
@RunWith(MockitoJUnitRunner.class)
public class UploadServiceMockTest {
    
    private static final DateTime START_TIME = DateTime.parse("2016-04-02T10:00:00.000Z");
    private static final DateTime END_TIME = DateTime.parse("2016-04-03T10:00:00.000Z");
    private static final String MOCK_OFFSET_KEY = "mock-offset-key";

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
        HealthDataRecord dummyRecord = HealthDataRecord.create();
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
    public void getUploadView() {
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setRecordId("test-record-id");
        mockUpload.setUploadId("with-record-id");
        when(mockDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        HealthDataRecord dummyRecord = HealthDataRecord.create();
        dummyRecord.setSchemaId("schema-id");
        when(mockHealthDataService.getRecordById("test-record-id")).thenReturn(dummyRecord);

        // execute and validate
        UploadView uploadView = svc.getUploadView("with-record-id");
        assertEquals("test-record-id", uploadView.getUpload().getRecordId());
        assertEquals("with-record-id", uploadView.getUpload().getUploadId());
        assertEquals("schema-id", uploadView.getHealthData().getSchemaId());
    }
    
    private void setupUploadMocks() {
        // Mock upload
        doReturn(UploadStatus.SUCCEEDED).when(mockUpload).getStatus();
        doReturn("record-id").when(mockUpload).getRecordId();
        
        // Failed mock upload
        doReturn(UploadStatus.REQUESTED).when(mockFailedUpload).getStatus();

        // Mock upload with record ID but no record
        Upload mockUploadWithNoRecord = mock(Upload.class);
        when(mockUploadWithNoRecord.getStatus()).thenReturn(UploadStatus.SUCCEEDED);
        when(mockUploadWithNoRecord.getRecordId()).thenReturn("missing-record-id");

        // Mock getUploads/getUpload calls
        List<Upload> results = ImmutableList.of(mockUpload, mockFailedUpload, mockUploadWithNoRecord);
        
        ForwardCursorPagedResourceList<Upload> pagedListWithoutOffsetKey = new ForwardCursorPagedResourceList<>(results, null)
                .withRequestParam(ResourceList.PAGE_SIZE, API_MAXIMUM_PAGE_SIZE);
        doReturn(pagedListWithoutOffsetKey).when(mockDao).getUploads(eq("ABC"), any(DateTime.class), any(DateTime.class), eq(0), eq(null));
        
        ForwardCursorPagedResourceList<Upload> pagedList = new ForwardCursorPagedResourceList<>(results, MOCK_OFFSET_KEY)
            .withRequestParam(ResourceList.PAGE_SIZE, API_MAXIMUM_PAGE_SIZE);
        doReturn(pagedList).when(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        doReturn(pagedList).when(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME, API_DEFAULT_PAGE_SIZE, null);
        
        // Mock the record returned from the validation status record
        doReturn("schema-id").when(mockRecord).getSchemaId();
        doReturn(10).when(mockRecord).getSchemaRevision();
        doReturn(HealthDataRecord.ExporterStatus.SUCCEEDED).when(mockRecord).getSynapseExporterStatus();
        // Mock UploadValidationStatus from health data record;
        doReturn(mockRecord).when(mockHealthDataService).getRecordById("record-id");
    }
    
    // Mock a successful and unsuccessful upload. The successful upload should call to get information 
    // from the health data record table (schema id/revision). All should be merged correctly in the 
    // resulting views.
    @Test
    public void canGetUploads() throws Exception {
        setupUploadMocks();
        ForwardCursorPagedResourceList<UploadView> returned = svc.getUploads("ABC", START_TIME, END_TIME, 0, null);
        
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
        validateUploadMocks(returned, null);
    }
    
    @Test
    public void canGetStudyUploads() throws Exception {
        setupUploadMocks();
        
        // Now verify the study uploads works
        ForwardCursorPagedResourceList<UploadView> returned = svc.getStudyUploads(TestConstants.TEST_STUDY,
                START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        
        verify(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        validateUploadMocks(returned, MOCK_OFFSET_KEY);
    }

    @Test
    public void canGetStudyUploadsWithoutPageSize() throws Exception {
        setupUploadMocks();

        svc.getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME, null, null);

        verify(mockDao).getStudyUploads(TestConstants.TEST_STUDY, START_TIME, END_TIME, API_DEFAULT_PAGE_SIZE, null);
    }

    private void validateUploadMocks(ForwardCursorPagedResourceList<UploadView> returned, String expectedOffsetKey) {
        verify(mockHealthDataService).getRecordById("record-id");
        verify(mockHealthDataService).getRecordById("missing-record-id");
        verifyNoMoreInteractions(mockHealthDataService);

        List<? extends UploadView> uploadList = returned.getItems();
        assertEquals(3, uploadList.size());

        assertEquals(API_MAXIMUM_PAGE_SIZE, returned.getRequestParams().get("pageSize"));
        assertEquals(expectedOffsetKey, returned.getNextPageOffsetKey());

        // The two sources of information are combined in the view.
        UploadView view = uploadList.get(0);
        assertEquals(UploadStatus.SUCCEEDED, view.getUpload().getStatus());
        assertEquals("record-id", view.getUpload().getRecordId());
        assertEquals("schema-id", view.getSchemaId());
        assertEquals(new Integer(10), view.getSchemaRevision());
        assertEquals(HealthDataRecord.ExporterStatus.SUCCEEDED, view.getHealthRecordExporterStatus());
        assertNull(view.getHealthData());

        UploadView failedView = uploadList.get(1);
        assertEquals(UploadStatus.REQUESTED, failedView.getUpload().getStatus());
        assertNull(failedView.getUpload().getRecordId());
        assertNull(failedView.getSchemaId());
        assertNull(failedView.getSchemaRevision());
        assertNull(failedView.getHealthRecordExporterStatus());

        UploadView viewWithNoRecord = uploadList.get(2);
        assertEquals(UploadStatus.SUCCEEDED, viewWithNoRecord.getUpload().getStatus());
        assertEquals("missing-record-id", viewWithNoRecord.getUpload().getRecordId());
        assertNull(viewWithNoRecord.getSchemaId());
        assertNull(viewWithNoRecord.getSchemaRevision());
        assertNull(viewWithNoRecord.getHealthRecordExporterStatus());
    }

    @Test
    public void canPassStartTimeOnly() {
        setupUploadMocks();
        
        svc.getUploads("ABC", START_TIME, null, 0, null);
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
    }
    
    @Test
    public void canPassEndTimeOnly() {
        setupUploadMocks();
        
        svc.getUploads("ABC", null, END_TIME, 0, null);
        verify(mockDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
    }
    
    @Test
    public void canPassNoTimes() {
        setupUploadMocks();
        
        ArgumentCaptor<DateTime> start = ArgumentCaptor.forClass(DateTime.class);
        ArgumentCaptor<DateTime> end = ArgumentCaptor.forClass(DateTime.class);
        
        svc.getUploads("ABC", null, null, 0, null);
        
        verify(mockDao).getUploads(eq("ABC"), start.capture(), end.capture(), eq(0), eq(null));
        
        DateTime actualStart = start.getValue();
        DateTime actualEnd = end.getValue();
        assertEquals(actualStart.plusDays(1), actualEnd);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifiesEndTimeNotBeforeStartTime() {
        svc.getUploads("ABC", END_TIME, START_TIME, 0, null);
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
