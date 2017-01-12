package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.DeleteEndpointRequest;
import com.amazonaws.services.sns.model.GetEndpointAttributesResult;
import com.amazonaws.services.sns.model.NotFoundException;
import com.amazonaws.services.sns.model.SetEndpointAttributesRequest;
import com.google.common.collect.Maps;
import com.newrelic.agent.deps.com.google.common.collect.Lists;

/**
 * This interacts with SNS and to test against SNS itself, we'd need to create client application 
 * registrations across all environments. Using mocks for this DAO test instead (DynamoDB is a 
 * known system at this point so mock tests are OK).
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamoNotificationRegistrationDaoTest {

    private static final String PLATFORM_ARN = "platformARN";
    private static final String GUID = BridgeUtils.generateGuid();
    private static final String HEALTH_CODE = "healthCode";
    private static final String ENDPOINT_ARN = "endpointARN";
    private static final String DEVICE_ID = "deviceId";
    private static final long CREATED_ON = 1484173675648L;
    private static final long MODIFIED_ON = 1484173687607L;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    AmazonSNSClient mockSnsClient;
    
    @Mock
    CreatePlatformEndpointResult mockCreatePlatformEndpointResult;
    
    @Mock
    GetEndpointAttributesResult mockGetEndpointAttributesResult;
    
    @Mock
    PaginatedQueryList<NotificationRegistration> paginatedQueryList;
    
    DynamoNotificationRegistrationDao dao;
    
    @Captor
    ArgumentCaptor<NotificationRegistration> notificationRegistrationCaptor;
    
    @Captor
    ArgumentCaptor<CreatePlatformEndpointRequest> createPlatformEndpointRequestCaptor;
    
    @Captor
    ArgumentCaptor<SetEndpointAttributesRequest> setEndpointAttributesRequestCaptor;
    
    @Captor
    ArgumentCaptor<DeleteEndpointRequest> deleteEndpointRequestCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoNotificationRegistration>> queryCaptor;
    
    @Before
    public void before() {
        dao = new DynamoNotificationRegistrationDao();
        dao.setNotificationRegistrationMapper(mockMapper);
        dao.setSnsClient(mockSnsClient);
    }
    
    @Test
    public void list() {
        List<NotificationRegistration> registrations = Lists.newArrayList(
                getStubRegistration(), getStubRegistration());
        
        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class), queryCaptor.capture());
        doReturn(registrations.stream()).when(paginatedQueryList).stream();

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(2, list.size());
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(HEALTH_CODE, query.getHashKeyValues().getHealthCode());
    }
    
    // Opted here to return an empty list, as it's a list operation and we're not asking for specific registration
    @Test
    public void listWhenNoneExist() {
        List<NotificationRegistration> registrations = Lists.newArrayList();

        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class), queryCaptor.capture());
        doReturn(registrations.stream()).when(paginatedQueryList).stream();

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(0, list.size());
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(HEALTH_CODE, query.getHashKeyValues().getHealthCode());
    }
    
    @Test
    public void create() {
        // No existing record
        List<NotificationRegistration> registrations = Lists.newArrayList();
        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class), queryCaptor.capture());
        doReturn(registrations.stream()).when(paginatedQueryList).stream();
        
        doReturn(ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        GuidHolder holder = dao.createRegistration(PLATFORM_ARN, HEALTH_CODE, registration);
        
        verify(mockSnsClient).createPlatformEndpoint(createPlatformEndpointRequestCaptor.capture());
        
        CreatePlatformEndpointRequest snsRequest = createPlatformEndpointRequestCaptor.getValue();
        assertEquals(PLATFORM_ARN, snsRequest.getPlatformApplicationArn());
        assertEquals(DEVICE_ID, snsRequest.getToken());
        assertEquals(HEALTH_CODE, snsRequest.getCustomUserData());
        
        assertNotNull(holder.getGuid());
        assertNotEquals(GUID, holder.getGuid());
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        
        NotificationRegistration reg = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, reg.getHealthCode());
        assertEquals(holder.getGuid(), reg.getGuid());
        assertEquals(ENDPOINT_ARN, reg.getEndpointARN());
        assertEquals(DEVICE_ID, reg.getDeviceId());
        assertEquals(OperatingSystem.IOS, reg.getOsName());
        assertTrue(reg.getCreatedOn() > 0L);
        assertTrue(reg.getModifiedOn() > 0L);
    }
    
    // In this case, we want to see the GUID returned and no duplicate record created. So 
    // that's the only part that's additional to the createNotifications() test when new.
    @Test
    public void createWhenItExists() {
        List<NotificationRegistration> registrations = Lists.newArrayList(getStubRegistration());
        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class), queryCaptor.capture());
        doReturn(registrations.stream()).when(paginatedQueryList).stream();

        doReturn(ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        GuidHolder holder = dao.createRegistration(PLATFORM_ARN, HEALTH_CODE, registration);
        assertEquals(GUID, holder.getGuid());
    }
    
    @Test
    public void get() {
        NotificationRegistration registration = getStubRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        NotificationRegistration returned = dao.getRegistration(HEALTH_CODE, GUID);
        
        verify(mockMapper).load(notificationRegistrationCaptor.capture());
        NotificationRegistration hashKey = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, hashKey.getHealthCode());
        assertEquals(GUID, hashKey.getGuid());
        assertEquals(registration, returned);
    }
    
    @Test
    public void getWhenItDoesNotExist() {
        doReturn(null).when(mockMapper).load(any());
        
        try {
            dao.getRegistration(HEALTH_CODE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals("NotificationRegistration not found.", e.getMessage());
        }
    }
    
    @Test
    public void updateNoChange() {
        NotificationRegistration registration = getStubRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", DEVICE_ID);
        map.put("Enabled", "true");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        dao.updateRegistration(PLATFORM_ARN, HEALTH_CODE, registration);
        
        verify(mockSnsClient, never()).setEndpointAttributes(any());
        verify(mockMapper, never()).save(any());
    }
    
    @Test
    public void updateWhenTokenHasChanged() {
        NotificationRegistration registration = getStubRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", "this is not the device id");
        map.put("Enabled", "false");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        dao.updateRegistration(PLATFORM_ARN, HEALTH_CODE, registration);
        
        verify(mockSnsClient).setEndpointAttributes(setEndpointAttributesRequestCaptor.capture());
        SetEndpointAttributesRequest request = setEndpointAttributesRequestCaptor.getValue();
        Map<String,String> attributes = request.getAttributes();
        assertEquals(DEVICE_ID, attributes.get("Token"));
        assertEquals("true", attributes.get("Enabled"));
        assertEquals(HEALTH_CODE, attributes.get("CustomUserData"));
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration persisted = notificationRegistrationCaptor.getValue();
        assertEquals(GUID, persisted.getGuid());
        assertEquals(HEALTH_CODE, persisted.getHealthCode());
        assertEquals(ENDPOINT_ARN, persisted.getEndpointARN());
        assertEquals(DEVICE_ID, persisted.getDeviceId());
        assertEquals(OperatingSystem.IOS, persisted.getOsName());
        assertEquals(CREATED_ON, persisted.getCreatedOn());
        assertNotEquals(MODIFIED_ON, persisted.getModifiedOn()); // modified is changed
        assertTrue(persisted.getModifiedOn() > 0L);
    }
    
    @Test
    public void updateWhenNew() {
        doReturn(null).when(mockMapper).load(any());
        doThrow(new NotFoundException("Error")).when(mockSnsClient).getEndpointAttributes(any());
        
        NotificationRegistration registration = getStubRegistration();
        try {
            dao.updateRegistration(PLATFORM_ARN, HEALTH_CODE, registration);
            fail("Should not have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }
    
    @Test
    public void delete() {
        NotificationRegistration registration = getStubRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        dao.deleteRegistration(HEALTH_CODE, GUID);
        
        verify(mockMapper).delete(notificationRegistrationCaptor.capture());
        NotificationRegistration del = notificationRegistrationCaptor.getValue();
        assertEquals(GUID, del.getGuid());
        assertEquals(HEALTH_CODE, del.getHealthCode());
        
        verify(mockSnsClient).deleteEndpoint(deleteEndpointRequestCaptor.capture());
        DeleteEndpointRequest request = deleteEndpointRequestCaptor.getValue();
        assertEquals(ENDPOINT_ARN, request.getEndpointArn());
    }
    
    @Test
    public void deleteWhenDoesNotExist() {
        doReturn(null).when(mockMapper).load(any());
        doThrow(new NotFoundException("Error")).when(mockSnsClient).getEndpointAttributes(any());
        
        try {
            dao.deleteRegistration(HEALTH_CODE, GUID);
            fail("Should not have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }
    
    private NotificationRegistration getStubRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setGuid(GUID);
        registration.setHealthCode(HEALTH_CODE);
        registration.setEndpointARN(ENDPOINT_ARN);
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        registration.setCreatedOn(CREATED_ON);
        registration.setModifiedOn(MODIFIED_ON);
        return registration;
    }
}
