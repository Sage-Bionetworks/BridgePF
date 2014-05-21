package org.sagebionetworks.bridge.services;

import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.springframework.beans.factory.BeanFactory;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.LimitExceededException;
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
        TestUtils.deleteAllHealthData();
        
        service = new HealthDataServiceImpl();
        createMapper = mock(DynamoDBMapper.class);
        updateMapper = mock(DynamoDBMapper.class);
        service.setCreateMapper(createMapper);
        service.setUpdateMapper(updateMapper);

        UserSessionData data = new UserSessionData();
        UserProfile profile = new UserProfile();
        profile.setOwnerId("1");
        data.setProfile(profile);
        
        SynapseClient client = mock(SynapseClient.class);
        try {
            doReturn(data).when(client).getUserSessionData();
        } catch(Throwable t) {
        }

        BeanFactory factory = mock(BeanFactory.class);
        doReturn(client).when(factory).getBean("synapseClient", SynapseClient.class);

        service.setBeanFactory(factory);
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
        for (int i=0; i < count; i++) {
            list.add(new DynamoHealthDataRecord("1:1:1", new HealthDataRecordImpl("A"+i, new Date().getTime(), new Date().getTime(), data)));
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
    public void appendHealthDataErrorNullRecord() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        service.appendHealthData(null, Lists.newArrayList(record));
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(0, 0, null);
        HealthDataRecord record = new HealthDataRecordImpl();
        service.appendHealthData(key, Lists.newArrayList(record));
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorBadRecord() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl();
        service.appendHealthData(key, Lists.newArrayList(record));
    }
    @Test(expected=BridgeServiceException.class)
    public void appendHealthDataErrorExistingRecord() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl();
        record.setRecordId("beluga");
        service.appendHealthData(key, Lists.newArrayList(record));
    }
    @Test
    @SuppressWarnings("unchecked")
    public void appendHealthDataSuccess() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = createHealthDataRecord();

        List<String> identifiers = service.appendHealthData(key, Lists.newArrayList(record));

        verify(createMapper).batchSave(any(List.class));
        assertThat(record.getRecordId()).isEqualTo(identifiers.get(0));
        
        verifyNoMoreInteractions(createMapper);
    }
    @Test(expected=BridgeServiceException.class)
    @SuppressWarnings("unchecked")
    public void appendHealthDataServiceError() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = createHealthDataRecord();
        
        doThrow(new LimitExceededException("Limit exceeded")).when(createMapper).batchSave(any(List.class));
        service.appendHealthData(key, Lists.newArrayList(record));
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

        List<DynamoHealthDataRecord> records = getRecordsFromDynamo(6);
        doReturn(records).when(updateMapper).query((Class<DynamoHealthDataRecord>)any(), (DynamoDBQueryExpression<DynamoHealthDataRecord>)any());
        
        List<HealthDataRecord> entries = service.getAllHealthData(key);
        assertThat(entries.size()).isEqualTo(6);
        assertThat(entries.get(0)).isInstanceOf(HealthDataRecord.class);
        assertThat(entries.get(0).getData()).isNotNull();
    }
    
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataRecordNullKey() throws Exception {
        service.getHealthDataRecord(null, "belgium");
    }
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataRecordBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(0, 0, null);
        service.getHealthDataRecord(key, "belgium");
    }
    @Test(expected=BridgeServiceException.class)
    public void getHealthDataRecordNullId() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        service.getHealthDataRecord(key, null);
    }
    @Test(expected=BridgeServiceException.class)
    @SuppressWarnings("unchecked")
    public void getHealthDataRecordInvalidId() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        
        doThrow(new BridgeServiceException("Test", 500)).when(createMapper).query((Class<DynamoHealthDataRecord>) any(),
                (DynamoDBQueryExpression<DynamoHealthDataRecord>) any());
        
        service.getHealthDataRecord(key, "foo");
    }
    @Test
    @SuppressWarnings("unchecked")
    public void getHealthDataRecordSuccess() throws Exception {
        long date = new Date().getTime();
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl("A0", date, date, null);
        
        List<DynamoHealthDataRecord> records = getRecordsFromDynamo(record);
        doReturn(records).when(updateMapper).load((Class<DynamoHealthDataRecord>)any());
        
        HealthDataRecord result = service.getHealthDataRecord(key, "foo");
        
        assertThat(result.getRecordId()).isEqualTo(record.getRecordId());
        assertThat(result.getStartDate()).isEqualTo(record.getStartDate());
        assertThat(result.getEndDate()).isEqualTo(record.getEndDate());
    }
    
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataRecordBadKey() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 0, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), null);
        
        service.updateHealthDataRecord(key, record);
        verify(updateMapper).save(any(DynamoHealthDataRecord.class));
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataRecordNoKey() throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), null);
        service.updateHealthDataRecord(null, record);
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataRecordBadRecord() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl("A0", 0, new Date().getTime(), null);
        service.updateHealthDataRecord(key, record);
    }
    @Test(expected=BridgeServiceException.class)
    public void updateHealthDataRecordNoRecord() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        service.updateHealthDataRecord(key, null);
    }
    @Test
    public void updateHealthDataSuccess() throws Exception {
        HealthDataKey key = new HealthDataKey(1, 1, "belgium");
        HealthDataRecord record = new HealthDataRecordImpl("A0", new Date().getTime(), new Date().getTime(), null);
        
        service.updateHealthDataRecord(key, record);
        verify(updateMapper).save(any(DynamoHealthDataRecord.class));
    }
}
