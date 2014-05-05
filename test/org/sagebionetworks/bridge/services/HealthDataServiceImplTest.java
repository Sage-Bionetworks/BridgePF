package org.sagebionetworks.bridge.services;

import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.healthdata.HealthDataEntry;
import org.sagebionetworks.bridge.healthdata.HealthDataEntryImpl;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;

import com.amazonaws.services.cloudsearchv2.model.LimitExceededException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.fest.assertions.Assertions.*;

public class HealthDataServiceImplTest {
    
    private HealthDataServiceImpl service;
    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;

    @Before
    public void before() {
        service = new HealthDataServiceImpl();
        createMapper = mock(DynamoDBMapper.class);
        updateMapper = mock(DynamoDBMapper.class);
        service.setCreateMapper(createMapper);
        service.setUpdateMapper(updateMapper);
    }

    private HealthDataEntry createHealthDataEntry() {
        Date date = new Date();
        HealthDataEntry entry = new HealthDataEntryImpl();
        entry.setStartDate(date.getTime());
        entry.setEndDate(date.getTime());
        return entry;
    }
    private PaginatedQueryList<DynamoRecord> getRecordsFromDynamo(int count) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"weight\": 20}");
        
        List<DynamoRecord> list = Lists.newArrayList(); 
        for (int i=0; i < count; i++) {
            list.add(new DynamoRecord("1:1:1", new HealthDataEntryImpl("A"+i, new Date().getTime(), new Date().getTime(), data)));
        }
        return toPaginatedQueryList(list);
    }
    private PaginatedQueryList<DynamoRecord> getRecordsFromDynamo(HealthDataEntry entry) throws Exception {
        List<DynamoRecord> list = Lists.newArrayList();
        list.add(new DynamoRecord("1:1:1", entry));
        return toPaginatedQueryList(list);
    }
    @SuppressWarnings("unchecked")
    private PaginatedQueryList<DynamoRecord> toPaginatedQueryList(final List<DynamoRecord> list) {
        PaginatedQueryList<DynamoRecord> results = mock(PaginatedQueryList.class);
        doReturn(list.iterator()).when(results).iterator();
        doReturn(list.size()).when(results).size();
        doReturn(list.get(0)).when(results).get(0);
        return results;
    }
    
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorBothNull() throws Exception {
        service.appendHealthData(null, null);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorNullKey() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        service.appendHealthData(key, null);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorNullEntry() throws Exception {
        HealthDataEntry entry = new HealthDataEntryImpl();
        service.appendHealthData(null, entry);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(0, 0, null);
        HealthDataEntry entry = new HealthDataEntryImpl();
        service.appendHealthData(key, entry);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorBadEntry() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl();
        service.appendHealthData(key, entry);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorExistingEntry() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl();
        entry.setId("beluga");
        service.appendHealthData(key, entry);
    }
    @Test
    public void appendHealthDataSuccess() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = createHealthDataEntry();

        String identifier = service.appendHealthData(key, entry);

        verify(createMapper).save(any(DynamoRecord.class));
        assertThat(entry.getId()).isEqualTo(identifier);
        
        verifyNoMoreInteractions(createMapper);
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataServiceError() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = createHealthDataEntry();

        doThrow(new LimitExceededException("Limit exceeded")).when(createMapper).save(any(DynamoRecord.class));
        service.appendHealthData(key, entry);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoStudy() throws Exception {
        HealthDataKey key = new HealthDataKey(0, 1, "belgium");
        service.getAllHealthData(key);
    }
    @Test(expected=BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoTracker() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 0, "belgium");
        service.getAllHealthData(key);
    }
    @Test(expected=BridgeServiceException.class)
    public void getAllHealthDataErrorBadKeyNoSessionToken() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, null);
        service.getAllHealthData(key);
    }
    @Test
    @SuppressWarnings("unchecked")
    public void getAllHealthDataSuccessful() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");

        List<DynamoRecord> records = getRecordsFromDynamo(6);
        doReturn(records).when(createMapper).query((Class<DynamoRecord>)any(), (DynamoDBQueryExpression<DynamoRecord>)any());
        
        List<HealthDataEntry> entries = service.getAllHealthData(key);
        assertThat(entries.size()).isEqualTo(6);
        assertThat(entries.get(0)).isInstanceOf(HealthDataEntry.class);
        assertThat(entries.get(0).getPayload()).isNotNull();
    }
    
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataEntryNullKey() throws Exception {
        service.getHealthDataEntry(null, "belgium");
    }
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataEntryBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(0, 0, null);
        service.getHealthDataEntry(key, "belgium");
    }
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataEntryNullId() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        service.getHealthDataEntry(key, null);
    }
    @Test(expected=BridgeServiceException.class)
    @SuppressWarnings("unchecked")
    public void getHealthDataEntryInvalidId() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        
        doThrow(new BridgeServiceException("Test")).when(createMapper).query((Class<DynamoRecord>) any(),
                (DynamoDBQueryExpression<DynamoRecord>) any());
        
        service.getHealthDataEntry(key, "foo");
    }
    @Test
    @SuppressWarnings("unchecked")
    public void getHealthDataEntrySuccess() throws Exception {
        long date = new Date().getTime();
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl("A0", date, date, null);
        
        List<DynamoRecord> records = getRecordsFromDynamo(entry);
        doReturn(records).when(createMapper).query((Class<DynamoRecord>)any(), (DynamoDBQueryExpression<DynamoRecord>)any());
        
        HealthDataEntry result = service.getHealthDataEntry(key, "foo");
        
        assertThat(result.getId()).isEqualTo(entry.getId());
        assertThat(result.getStartDate()).isEqualTo(entry.getStartDate());
        assertThat(result.getEndDate()).isEqualTo(entry.getEndDate());
    }
    
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataEntryBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 0, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl("A0", new Date().getTime(), new Date().getTime(), null);
        
        service.updateHealthDataEntry(key, entry);
        verify(updateMapper).save(any(DynamoRecord.class));
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataEntryNoKey() throws Exception {
        HealthDataEntry entry = new HealthDataEntryImpl("A0", new Date().getTime(), new Date().getTime(), null);
        service.updateHealthDataEntry(null, entry);
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataEntryBadEntry() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl("A0", 0, new Date().getTime(), null);
        service.updateHealthDataEntry(key, entry);
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataEntryNoEntry() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        service.updateHealthDataEntry(key, null);
    }
    @Test
    public void updateHealthDataSuccess() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataEntry entry = new HealthDataEntryImpl("A0", new Date().getTime(), new Date().getTime(), null);
        
        service.updateHealthDataEntry(key, entry);
        verify(updateMapper).save(any(DynamoRecord.class));
    }
}
