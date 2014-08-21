package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.STUDY;

import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.IdVersionHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.LimitExceededException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class HealthDataServiceImplTest {

    private HealthDataServiceImpl service;
    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;
    private User user;
    private HealthDataKey key;
    private Tracker tracker;

    @Before
    public void before() {
        tracker = new Tracker();
        tracker.setId(1L);

        createMapper = mock(DynamoDBMapper.class);
        updateMapper = mock(DynamoDBMapper.class);

        user = new User();
        user.setHealthDataCode("1");
        key = new HealthDataKey(STUDY, tracker, user);
        
        service = new HealthDataServiceImpl();
        service.setCreateMapper(createMapper);
        service.setUpdateMapper(updateMapper);
    }

    private HealthDataRecord createHealthDataRecord() {
        Date date = new Date();
        HealthDataRecord record = new HealthDataRecordImpl();
        record.setStartDate(date.getTime());
        record.setEndDate(date.getTime());
        return record;
    }

    private PaginatedQueryList<DynamoHealthDataRecord> getRecordsFromDynamo(int count) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"weight\": 20}");

        List<DynamoHealthDataRecord> list = Lists.newArrayList();
        for (int i = 0; i < count; i++) {
            list.add(new DynamoHealthDataRecord("1:1:1", new HealthDataRecordImpl("A" + i, new Date().getTime(),
                    new Date().getTime(), 1L, data)));
        }
        return toPaginatedQueryList(list);
    }

    private PaginatedQueryList<DynamoHealthDataRecord> getRecordsFromDynamo(HealthDataRecord record) throws Exception {
        List<DynamoHealthDataRecord> list = Lists.newArrayList();
        list.add(new DynamoHealthDataRecord("1:1:1", record));
        return toPaginatedQueryList(list);
    }

    @SuppressWarnings("unchecked")
    private PaginatedQueryList<DynamoHealthDataRecord> toPaginatedQueryList(final List<DynamoHealthDataRecord> list) {
        PaginatedQueryList<DynamoHealthDataRecord> results = mock(PaginatedQueryList.class);
        doReturn(list.iterator()).when(results).iterator();
        doReturn(list.size()).when(results).size();
        doReturn(list.get(0)).when(results).get(0);
        return results;
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorBothNull() throws Exception {
        service.appendHealthData(null, null);
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorNullKey() throws Exception {
        service.appendHealthData(key, null);
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorNullRecord() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        service.appendHealthData(null, Lists.newArrayList(record));
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorBadKey() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        // Must recreate the key because it copies the invalid tracker ID on construction
        tracker.setId(0L);
        service.appendHealthData(new HealthDataKey(STUDY, tracker, user), Lists.newArrayList(record));
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorBadRecord() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        service.appendHealthData(key, Lists.newArrayList(record));
    }

    @Test(expected = BridgeServiceException.class)
    public void appendHealthDataErrorExistingRecord() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        record.setRecordId("beluga");
        service.appendHealthData(key, Lists.newArrayList(record));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void appendHealthDataSuccess() throws Exception {
        HealthDataRecord record = createHealthDataRecord();

        List<IdVersionHolder> identifiers = service.appendHealthData(key, Lists.newArrayList(record));

        verify(createMapper).batchSave(any(List.class));

        assertEquals("Returns the assigned ID for health data record", identifiers.get(0).getId(), record.getRecordId());

        verifyNoMoreInteractions(createMapper);
    }

    @Test(expected = BridgeServiceException.class)
    @SuppressWarnings("unchecked")
    public void appendHealthDataServiceError() throws Exception {
        HealthDataRecord record = createHealthDataRecord();

        doThrow(new LimitExceededException("Limit exceeded")).when(createMapper).batchSave(any(List.class));
        service.appendHealthData(key, Lists.newArrayList(record));
    }

    @Test(expected = BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoStudy() throws Exception {
        Study badStudy = new Study("", null, -12, null, null, null, null);
        HealthDataKey key = new HealthDataKey(badStudy, tracker, user);
        service.getAllHealthData(key);
    }

    @Test(expected = BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoTracker() throws Exception {
        tracker.setId(null);
        service.getAllHealthData(key);
    }

    @Test(expected = BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoSessionToken() throws Exception {
        service.getAllHealthData(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getAllHealthDataSuccessful() throws Exception {

        List<DynamoHealthDataRecord> records = getRecordsFromDynamo(6);
        doReturn(records).when(updateMapper).query((Class<DynamoHealthDataRecord>) any(),
                (DynamoDBQueryExpression<DynamoHealthDataRecord>) any());

        List<HealthDataRecord> entries = service.getAllHealthData(key);

        assertEquals("Returns 6 records", 6, entries.size());
        assertTrue("Returns HealthDataRecord objects", entries.get(0) instanceof HealthDataRecord);
        assertNotNull("Data for first record is not null", entries.get(0).getData());
    }

    @Test(expected = BridgeServiceException.class)
    public void getHealthDataRecordNullKey() throws Exception {
        service.getHealthDataRecord(null, "belgium");
    }

    @Test(expected = BridgeServiceException.class)
    public void getHealthDataRecordBadKey() throws Exception {
        Study badStudy = new Study("", "", 0, null, null, null, null);
        tracker.setId(0L);
        HealthDataKey key = new HealthDataKey(badStudy, tracker, null);
        service.getHealthDataRecord(key, "belgium");
    }

    @Test(expected = BridgeServiceException.class)
    public void getHealthDataRecordNullId() throws Exception {
        service.getHealthDataRecord(key, null);
    }

    @Test(expected = BridgeServiceException.class)
    @SuppressWarnings("unchecked")
    public void getHealthDataRecordInvalidId() throws Exception {
        doThrow(new BridgeServiceException("Test", 500)).when(createMapper).query(
                (Class<DynamoHealthDataRecord>) any(), (DynamoDBQueryExpression<DynamoHealthDataRecord>) any());

        service.getHealthDataRecord(key, "foo");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getHealthDataRecordSuccess() throws Exception {
        long date = new Date().getTime();
        HealthDataRecord record = new HealthDataRecordImpl("A0", date, date, 1L, null);

        List<DynamoHealthDataRecord> records = getRecordsFromDynamo(record);
        doReturn(records.get(0)).when(updateMapper).load((Class<DynamoHealthDataRecord>) any());

        HealthDataRecord result = service.getHealthDataRecord(key, "foo");

        assertEquals("Returns correct record ID", record.getRecordId(), result.getRecordId());
        assertEquals("Returns correct start date", record.getStartDate(), result.getStartDate());
        assertEquals("Returns correct end date", record.getEndDate(), result.getEndDate());
    }

    @Test(expected = BridgeServiceException.class)
    public void updateHealthDataRecordBadKey() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), 1L, null);
        tracker.setId(0L);
        service.updateHealthDataRecord(new HealthDataKey(STUDY, tracker, user), record);
        verify(updateMapper).save(any(DynamoHealthDataRecord.class));
    }

    @Test(expected = BridgeServiceException.class)
    public void updateHealthDataRecordNoKey() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), 1L, null);
        service.updateHealthDataRecord(null, record);
    }

    @Test(expected = BridgeServiceException.class)
    public void updateHealthDataRecordBadRecord() throws Exception {
        long time = new Date().getTime();
        HealthDataRecord record = new HealthDataRecordImpl("A0", 0, time, time, null);
        service.updateHealthDataRecord(key, record);
    }

    @Test(expected = BridgeServiceException.class)
    public void updateHealthDataRecordNoRecord() throws Exception {
        service.updateHealthDataRecord(key, null);
    }

    @Test
    public void updateHealthDataSuccess() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), 1L, null);

        service.updateHealthDataRecord(key, record);
        verify(updateMapper).save(any(DynamoHealthDataRecord.class));
    }
}
