package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public class HealthDataServiceTest {
    // We want to do as much testing as possible through the generic interface, so we have this DAO that we use just
    // for getRecordBuilder().
    private static final HealthDataDao DAO = new DynamoHealthDataDao();

    @Test(expected = InvalidEntityException.class)
    public void createRecordNullRecord() {
        new HealthDataService().createRecord(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void createRecordInvalidRecord() {
        // build and overwrite data
        DynamoHealthDataRecord record = (DynamoHealthDataRecord) DAO.getRecordBuilder()
                .withHealthCode("valid healthcode").withSchemaId("valid schema").build();
        record.setData(null);

        // execute
        new HealthDataService().createRecord(record);
    }

    @Test
    public void createRecordSuccess() {
        // record
        HealthDataRecord record = DAO.getRecordBuilder().withHealthCode("valid healthcode")
                .withSchemaId("valid schema").build();

        // mock dao
        HealthDataDao mockDao = mock(HealthDataDao.class);
        when(mockDao.createRecord(record)).thenReturn("mock record ID");

        HealthDataService svc = new HealthDataService();
        svc.setHealthDataDao(mockDao);

        // execute and validate
        String retVal = svc.createRecord(record);
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
                DAO.getRecordBuilder().withHealthCode("foo healthcode").withSchemaId("dummy schema").build(),
                DAO.getRecordBuilder().withHealthCode("bar healthcode").withSchemaId("dummy schema").build(),
                DAO.getRecordBuilder().withHealthCode("baz healthcode").withSchemaId("dummy schema").build());

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
}
