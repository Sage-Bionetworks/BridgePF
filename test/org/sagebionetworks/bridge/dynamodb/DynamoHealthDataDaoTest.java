package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DynamoHealthDataDaoTest {
    private static final String TEST_HEALTH_CODE = "1234";
    private static final Long TEST_CREATED_ON = Long.parseLong("1427970429000");
    private static final String TEST_SCHEMA_ID = "api";

    @Test
    public void createOrUpdateRecord() {
        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);

        // execute
        String id = dao.createOrUpdateRecord(new DynamoHealthDataRecord());

        // validate that the returned ID matches the ID received by the DDB mapper
        ArgumentCaptor<DynamoHealthDataRecord> arg = ArgumentCaptor.forClass(DynamoHealthDataRecord.class);
        verify(mockMapper).save(arg.capture());
        assertEquals(id, arg.getValue().getId());
    }

    @Test
    public void deleteRecordsForHealthCode() {
        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<List> arg = ArgumentCaptor.forClass(List.class);
        when(mockMapper.batchDelete(arg.capture())).thenReturn(Collections.<DynamoDBMapper.FailedBatch>emptyList());

        // mock index helper
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setHealthCode("test health code");
        record.setId("test ID");
        List<HealthDataRecord> mockResult = Collections.<HealthDataRecord>singletonList(record);

        DynamoIndexHelper mockIndex = mock(DynamoIndexHelper.class);
        when(mockIndex.queryKeys(HealthDataRecord.class, "healthCode", "test health code", null)).thenReturn(mockResult);

        // set up and execute
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);
        dao.setHealthCodeIndex(mockIndex);
        int numDeleted = dao.deleteRecordsForHealthCode("test health code");
        assertEquals(1, numDeleted);

        // validate intermediate results
        List<HealthDataRecord> recordKeyList = arg.getValue();
        assertEquals(1, recordKeyList.size());
        assertEquals("test health code", recordKeyList.get(0).getHealthCode());
        assertEquals("test ID", recordKeyList.get(0).getId());
    }

    @Test
    public void deleteRecordsForHealthCodeMapperException() {
        // mock failed batch
        // we have to mock extra stuff because BridgeUtils.ifFailuresThrowException() checks all these things
        DynamoDBMapper.FailedBatch failure = new DynamoDBMapper.FailedBatch();
        failure.setException(new Exception("dummy exception message"));
        failure.setUnprocessedItems(Collections.<String, List<WriteRequest>>emptyMap());

        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<List> arg = ArgumentCaptor.forClass(List.class);
        when(mockMapper.batchDelete(arg.capture())).thenReturn(Collections.singletonList(failure));

        // mock index helper
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setHealthCode("test health code");
        record.setId("error record");
        List<HealthDataRecord> mockResult = Collections.<HealthDataRecord>singletonList(record);

        DynamoIndexHelper mockIndex = mock(DynamoIndexHelper.class);
        when(mockIndex.queryKeys(HealthDataRecord.class, "healthCode", "test health code", null)).thenReturn(mockResult);

        // set up
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);
        dao.setHealthCodeIndex(mockIndex);

        // execute and validate exception
        Exception thrownEx = null;
        try {
            dao.deleteRecordsForHealthCode("test health code");
            fail();
        } catch (BridgeServiceException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate results
        List<HealthDataRecord> recordKeyList = arg.getValue();
        assertEquals(1, recordKeyList.size());
        assertEquals("test health code", recordKeyList.get(0).getHealthCode());
        assertEquals("error record", recordKeyList.get(0).getId());
    }

    @Test
    public void getRecordsForUploadDate() {
        // mock index helper
        List<HealthDataRecord> mockResult = Collections.emptyList();
        DynamoIndexHelper mockIndex = mock(DynamoIndexHelper.class);
        when(mockIndex.query(HealthDataRecord.class, "uploadDate", "2015-02-11", null)).thenReturn(mockResult);

        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setUploadDateIndex(mockIndex);

        // execute and validate
        List<HealthDataRecord> retVal = dao.getRecordsForUploadDate("2015-02-11");
        assertSame(mockResult, retVal);
    }

    @Test
    public void getRecordsByHealthCodeCreatedOnSchemaId() {
        // mock index helper
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setHealthCode(TEST_HEALTH_CODE);
        record.setId("test ID");
        record.setCreatedOn(TEST_CREATED_ON);
        record.setSchemaId(TEST_SCHEMA_ID);

        List<DynamoHealthDataRecord> mockResult = ImmutableList.of(record);
        DynamoHealthDataDao dao = new DynamoHealthDataDao();

        // mock mapper
        PaginatedQueryList<DynamoHealthDataRecord> mockGetResult = mock(PaginatedQueryList.class);
        when(mockGetResult.isEmpty()).thenReturn(false);
        when(mockGetResult.iterator()).thenReturn(mockResult.iterator()); // mock iterator to handle foreach loop
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.query(eq(DynamoHealthDataRecord.class), any())).thenReturn(mockGetResult);

        dao.setMapper(mockMapper);

        // execute and validate
        List<HealthDataRecord> retVal = dao.getRecordsByHealthCodeCreatedOnSchemaId(TEST_HEALTH_CODE, TEST_CREATED_ON, TEST_SCHEMA_ID);

        assertEquals(mockResult, retVal);
    }

    @Test
    public void getRecordsByHealthCodeCreatedOnSchemaIdMaxRecords() {
        // mock result
        List<DynamoHealthDataRecord> recordList = new ArrayList<>();

        // For branch coverage, first record has the wrong schema ID.
        DynamoHealthDataRecord wrongSchemaRecord = new DynamoHealthDataRecord();
        wrongSchemaRecord.setId("wrong-schema-record");
        wrongSchemaRecord.setSchemaId("wrong-" + TEST_SCHEMA_ID);
        recordList.add(wrongSchemaRecord);

        // The only thing we look for in the results is schema ID. Also add recordID so we can test our results.
        // Make a list of 20 more records to test the max dupe records logic.
        for (int i = 0; i < 20; i++) {
            DynamoHealthDataRecord oneRecord = new DynamoHealthDataRecord();
            oneRecord.setId("record-" + i);
            oneRecord.setSchemaId(TEST_SCHEMA_ID);
            recordList.add(oneRecord);
        }

        // mock DDB query
        PaginatedQueryList<DynamoHealthDataRecord> mockGetResult = mock(PaginatedQueryList.class);
        when(mockGetResult.isEmpty()).thenReturn(false);
        when(mockGetResult.iterator()).thenReturn(recordList.iterator()); // mock iterator to handle foreach loop

        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.query(eq(DynamoHealthDataRecord.class), any())).thenReturn(mockGetResult);

        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);

        // execute and validate
        List<HealthDataRecord> resultRecordList = dao.getRecordsByHealthCodeCreatedOnSchemaId(TEST_HEALTH_CODE,
                TEST_CREATED_ON, TEST_SCHEMA_ID);
        assertEquals(BridgeConstants.DUPE_RECORDS_MAX_COUNT, resultRecordList.size());

        for (int i = 0; i < BridgeConstants.DUPE_RECORDS_MAX_COUNT; i++) {
            HealthDataRecord oneResultRecord = resultRecordList.get(i);
            assertEquals("record-" + i, oneResultRecord.getId());
            assertEquals(TEST_SCHEMA_ID, oneResultRecord.getSchemaId());
        }
    }
}
