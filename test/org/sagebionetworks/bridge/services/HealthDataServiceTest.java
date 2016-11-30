package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;

public class HealthDataServiceTest {
    // We want to do as much testing as possible through the generic interface, so we have this DAO that we use just
    // for getRecordBuilder().
    private static final HealthDataDao DAO = new DynamoHealthDataDao();

    private static final String TEST_HEALTH_CODE = "valid healthcode";
    private static final String TEST_SCHEMA_ID = "valid schema";
    private static final String TEST_STUDY_ID = "valid study";
    private static final String TEST_RECORD_ID = "mock record ID";
    private static final String TEST_RECORD_ID_2 = "mock record ID 2";
    private static final Long TEST_CREATED_ON = Long.parseLong("1427970429000");

    @Test(expected = InvalidEntityException.class)
    public void createOrUpdateRecordNullRecord() {
        new HealthDataService().createOrUpdateRecord(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void createOrUpdateRecordInvalidRecord() {
        // build and overwrite data
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").withSchemaRevision(3)
                .withStudyId("valid study").build();
        record.setData(null);

        // execute
        new HealthDataService().createOrUpdateRecord(record);
    }

    @Test
    public void createOrUpdateRecordSuccess() {
        // record
        HealthDataRecord record = DAO.getRecordBuilder().withHealthCode("valid healthcode")
                .withSchemaId("valid schema").withSchemaRevision(3).withStudyId("valid study").build();

        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.createOrUpdateRecord(record)).thenReturn("mock record ID");

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        String retVal = svc.createOrUpdateRecord(record);
        assertEquals("mock record ID", retVal);
    }

    @Test(expected = BadRequestException.class)
    public void deleteRecordsForHealthCodeNullHealthCode() {
        new HealthDataService().deleteRecordsForHealthCode(null);
    }

    @Test(expected = BadRequestException.class)
    public void deleteRecordsForHealthCodeEmptyHealthCode() {
        new HealthDataService().deleteRecordsForHealthCode("");
    }

    @Test
    public void deleteHealthRecodsForHealthCodeSuccess() {
        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.deleteRecordsForHealthCode("test health code")).thenReturn(37);
        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and verify
        int numDeleted = svc.deleteRecordsForHealthCode("test health code");
        assertEquals(37, numDeleted);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsForUploadDateNullUploadDate() {
        new HealthDataService().getRecordsForUploadDate(null);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsForUploadDateEmptyUploadDate() {
        new HealthDataService().getRecordsForUploadDate("");
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsForUploadDateMalformedUploadDate() {
        new HealthDataService().getRecordsForUploadDate("This is not a calendar date.");
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsForUploadDateInvalidUploadDate() {
        new HealthDataService().getRecordsForUploadDate("2014-02-31");
    }

    @Test
    public void getRecordsForUploadDateSuccess() {
        // mock results
        List<HealthDataRecord> mockRecordList = ImmutableList.of(
                DAO.getRecordBuilder().withHealthCode("foo healthcode").withSchemaId("dummy schema")
                        .withSchemaRevision(3).withStudyId("dummy study").build(),
                DAO.getRecordBuilder().withHealthCode("bar healthcode").withSchemaId("dummy schema")
                        .withSchemaRevision(3).withStudyId("dummy study").build(),
                DAO.getRecordBuilder().withHealthCode("baz healthcode").withSchemaId("dummy schema")
                        .withSchemaRevision(3).withStudyId("dummy study").build());

        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsForUploadDate("2014-02-12")).thenReturn(mockRecordList);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        List<HealthDataRecord> recordList = svc.getRecordsForUploadDate("2014-02-12");
        assertEquals(3, recordList.size());
        assertEquals("foo healthcode", recordList.get(0).getHealthCode());
        assertEquals("bar healthcode", recordList.get(1).getHealthCode());
        assertEquals("baz healthcode", recordList.get(2).getHealthCode());
    }

    @Test(expected = InvalidEntityException.class)
    public void updateRecordsWithExporterStatusNullRecordIds() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);
        new HealthDataService().updateRecordsWithExporterStatus(request);
    }

    @Test(expected = InvalidEntityException.class)
    public void updateRecordsWithExporterStatusEmptyRecordIds() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setRecordIds(Arrays.asList());
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);
        new HealthDataService().updateRecordsWithExporterStatus(request);
    }

    @Test(expected = InvalidEntityException.class)
    public void updateRecordsWithExporterStatusNullStatus() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setRecordIds(Arrays.asList(TEST_RECORD_ID));
        new HealthDataService().updateRecordsWithExporterStatus(request);
    }

    @Test(expected = BadRequestException.class)
    public void updateRecordsWithExporterStatusExceedRecordIdListLimit() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        List<String> recordIds = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            recordIds.add(TEST_RECORD_ID);
        }
        request.setRecordIds(recordIds);
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);
        new HealthDataService().updateRecordsWithExporterStatus(request);
    }

    @Test
    public void updateRecordSuccess() throws Exception {

        // first create a mock record
        // record
        HealthDataRecord record = DAO.getRecordBuilder().withHealthCode(TEST_HEALTH_CODE)
                .withSchemaId(TEST_SCHEMA_ID).withSchemaRevision(3).withStudyId(TEST_STUDY_ID).build();

        HealthDataRecord record2 = DAO.getRecordBuilder().withHealthCode(TEST_HEALTH_CODE)
                .withSchemaId(TEST_SCHEMA_ID).withSchemaRevision(3).withStudyId(TEST_STUDY_ID).build();


        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.createOrUpdateRecord(record)).thenReturn(TEST_RECORD_ID);
        when(mockDao.createOrUpdateRecord(record2)).thenReturn(TEST_RECORD_ID_2);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        String retVal = svc.createOrUpdateRecord(record);
        assertEquals(TEST_RECORD_ID, retVal);

        // then create a mock json request
        RecordExportStatusRequest recordExportStatusRequest = createMockRecordExportStatusRequest();
        when(mockDao.getRecordById(TEST_RECORD_ID)).thenReturn(record);
        when(mockDao.getRecordById(TEST_RECORD_ID_2)).thenReturn(record2);

        // finally call service method and assert
        svc.updateRecordsWithExporterStatus(recordExportStatusRequest);
        verify(mockDao).getRecordById(TEST_RECORD_ID);
        HealthDataRecord recordAfter = svc.getRecordById(TEST_RECORD_ID);
        assertNotNull(recordAfter.getSynapseExporterStatus());
        assertEquals(HealthDataRecord.ExporterStatus.SUCCEEDED, recordAfter.getSynapseExporterStatus());
        verify(mockDao).getRecordById(TEST_RECORD_ID_2);
        HealthDataRecord record2After = svc.getRecordById(TEST_RECORD_ID_2);
        assertNotNull(record2After.getSynapseExporterStatus());
        assertEquals(HealthDataRecord.ExporterStatus.SUCCEEDED, record2After.getSynapseExporterStatus());

    }

    private RecordExportStatusRequest createMockRecordExportStatusRequest() throws Exception {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setRecordIds(Arrays.asList(TEST_RECORD_ID, TEST_RECORD_ID_2));
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);

        return request;
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByEmptyHealthcodeCreatedOnSchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId("", TEST_CREATED_ON, TEST_SCHEMA_ID);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByNullHealthcodeCreatedOnSchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(null, TEST_CREATED_ON, TEST_SCHEMA_ID);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthcodeNullCreatedOnSchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, null, TEST_SCHEMA_ID);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthcodeCreatedOnEmptySchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, "");
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthcodeCreatedOnNullSchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, null);
    }

    @Test
    public void getRecordsByHealthcodeCreatedOnSchemaId() {
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setHealthCode(TEST_HEALTH_CODE);
        record.setId("test ID");
        record.setCreatedOn(TEST_CREATED_ON);
        record.setSchemaId(TEST_SCHEMA_ID);

        List<HealthDataRecord> mockResult = Arrays.asList(record);

        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsByHealthCodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, TEST_SCHEMA_ID)).thenReturn(mockResult);
        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and verify
        List<HealthDataRecord> retList = svc.getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, TEST_SCHEMA_ID);
        assertEquals(mockResult, retList);
    }
}
