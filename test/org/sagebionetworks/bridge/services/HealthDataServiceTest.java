package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;

public class HealthDataServiceTest {
    private static final long TEST_CREATED_ON = 1427970429000L;
    private static final DateTime TEST_CREATED_ON_DATE_TIME = new DateTime(TEST_CREATED_ON);
    private static final long TEST_CREATED_ON_END = 1427970471979L;
    private static final DateTime TEST_CREATED_ON_END_DATE_TIME = new DateTime(TEST_CREATED_ON_END);
    private static final JsonNode TEST_DATA = BridgeObjectMapper.get().createObjectNode();
    private static final String TEST_HEALTH_CODE = "valid healthcode";
    private static final JsonNode TEST_METADATA = BridgeObjectMapper.get().createObjectNode();
    private static final String TEST_RECORD_ID = "mock record ID";
    private static final String TEST_RECORD_ID_2 = "mock record ID 2";
    private static final String TEST_SCHEMA_ID = "valid schema";
    private static final int TEST_SCHEMA_REV = 3;

    private static final String TEST_UPLOAD_DATE_STR = "2017-08-11";
    private static final LocalDate TEST_UPLOAD_DATE = LocalDate.parse(TEST_UPLOAD_DATE_STR);

    @Test(expected = InvalidEntityException.class)
    public void createOrUpdateRecordNullRecord() {
        new HealthDataService().createOrUpdateRecord(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void createOrUpdateRecordInvalidRecord() {
        new HealthDataService().createOrUpdateRecord(HealthDataRecord.create());
    }

    @Test
    public void createOrUpdateRecordSuccess() {
        // mock dao
        HealthDataRecord record = makeValidRecord();
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.createOrUpdateRecord(record)).thenReturn(TEST_RECORD_ID);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        String retVal = svc.createOrUpdateRecord(record);
        assertEquals(TEST_RECORD_ID, retVal);
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
        when(mockDao.deleteRecordsForHealthCode(TEST_HEALTH_CODE)).thenReturn(37);
        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and verify
        int numDeleted = svc.deleteRecordsForHealthCode(TEST_HEALTH_CODE);
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
        HealthDataRecord fooRecord = makeValidRecord();
        fooRecord.setId("foo record");
        fooRecord.setHealthCode("foo healthcode");

        HealthDataRecord barRecord = makeValidRecord();
        barRecord.setId("bar record");
        barRecord.setHealthCode("bar healthcode");

        HealthDataRecord bazRecord = makeValidRecord();
        bazRecord.setId("baz record");
        bazRecord.setHealthCode("baz healthcode");

        List<HealthDataRecord> mockRecordList = ImmutableList.of(fooRecord, barRecord, bazRecord);
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsForUploadDate(TEST_UPLOAD_DATE_STR)).thenReturn(mockRecordList);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        List<HealthDataRecord> recordList = svc.getRecordsForUploadDate(TEST_UPLOAD_DATE_STR);
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
        request.setRecordIds(ImmutableList.of());
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);
        new HealthDataService().updateRecordsWithExporterStatus(request);
    }

    @Test(expected = InvalidEntityException.class)
    public void updateRecordsWithExporterStatusNullStatus() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setRecordIds(ImmutableList.of(TEST_RECORD_ID));
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
    public void updateRecordSuccess() {
        // first create a mock record
        // record
        HealthDataRecord record = makeValidRecord();
        record.setId(TEST_RECORD_ID);

        HealthDataRecord record2 = makeValidRecord();
        record.setId(TEST_RECORD_ID_2);

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

    private RecordExportStatusRequest createMockRecordExportStatusRequest() {
        RecordExportStatusRequest request = new RecordExportStatusRequest();
        request.setRecordIds(ImmutableList.of(TEST_RECORD_ID, TEST_RECORD_ID_2));
        request.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED);

        return request;
    }

    @Test(expected = IllegalArgumentException.class)
    public void getRecordsByHealthCodeCreatedOn_NullHealthCode() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn(null, TEST_CREATED_ON_DATE_TIME,
                TEST_CREATED_ON_END_DATE_TIME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getRecordsByHealthCodeCreatedOn_EmptyHealthCode() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn("", TEST_CREATED_ON_DATE_TIME,
                TEST_CREATED_ON_END_DATE_TIME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getRecordsByHealthCodeCreatedOn_BlankHealthCode() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn("   ", TEST_CREATED_ON_DATE_TIME,
                TEST_CREATED_ON_END_DATE_TIME);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthCodeCreatedOn_NullCreatedOnStart() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, null,
                TEST_CREATED_ON_END_DATE_TIME);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthCodeCreatedOn_NullCreatedOnEnd() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON_DATE_TIME,
                null);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthCodeCreatedOn_StartOnAfterEndOn() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON_END_DATE_TIME,
                TEST_CREATED_ON_DATE_TIME);
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthCodeCreatedOn_DateRangeTooLarge() {
        new HealthDataService().getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON_DATE_TIME,
                TEST_CREATED_ON_DATE_TIME.plusDays(16));
    }

    @Test
    public void getRecordsByHealthCodeCreatedOn_Success() {
        // Mock DAO.
        List<HealthDataRecord> mockResult = ImmutableList.of(makeValidRecord());
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON, TEST_CREATED_ON_END)).
                thenReturn(mockResult);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // Execute and verify.
        List<HealthDataRecord> retList = svc.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE,
                TEST_CREATED_ON_DATE_TIME, TEST_CREATED_ON_END_DATE_TIME);
        assertEquals(mockResult, retList);
    }

    @Test
    public void getRecordsByHealthCodeCreatedOn_SameTimeOkay() {
        // Mock DAO.
        List<HealthDataRecord> mockResult = ImmutableList.of(makeValidRecord());
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON, TEST_CREATED_ON)).
                thenReturn(mockResult);

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // Execute and verify.
        List<HealthDataRecord> retList = svc.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE,
                TEST_CREATED_ON_DATE_TIME, TEST_CREATED_ON_DATE_TIME);
        assertEquals(mockResult, retList);
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
    public void getRecordsByHealthcodeCreatedOnEmptySchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, "");
    }

    @Test(expected = BadRequestException.class)
    public void getRecordsByHealthcodeCreatedOnNullSchemaId() {
        new HealthDataService().getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, null);
    }

    @Test
    public void getRecordsByHealthcodeCreatedOnSchemaId() {
        // For branch coverage, first record has the wrong schema ID.
        HealthDataRecord wrongSchemaRecord = makeValidRecord();
        wrongSchemaRecord.setId("wrong-schema-record");
        wrongSchemaRecord.setSchemaId("wrong-" + TEST_SCHEMA_ID);

        // Normal record, which will be returned by our mock.
        HealthDataRecord record = makeValidRecord();

        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE,
                TEST_CREATED_ON - HealthDataService.CREATED_ON_OFFSET_MILLIS,
                TEST_CREATED_ON + HealthDataService.CREATED_ON_OFFSET_MILLIS))
                .thenReturn(ImmutableList.of(wrongSchemaRecord, record));

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and verify
        List<HealthDataRecord> retList = svc.getRecordsByHealthcodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON,
                TEST_SCHEMA_ID);
        assertEquals(1, retList.size());
        assertSame(record, retList.get(0));
    }

    private static HealthDataRecord makeValidRecord() {
        HealthDataRecord record = HealthDataRecord.create();
        record.setCreatedOn(TEST_CREATED_ON);
        record.setData(TEST_DATA);
        record.setHealthCode(TEST_HEALTH_CODE);
        record.setMetadata(TEST_METADATA);
        record.setSchemaId(TEST_SCHEMA_ID);
        record.setSchemaRevision(TEST_SCHEMA_REV);
        record.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        record.setUploadDate(TEST_UPLOAD_DATE);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setUserSharingScope(SharingScope.NO_SHARING);
        return record;
    }
}
