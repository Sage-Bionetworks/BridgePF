package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.getNotificationRegistration;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.DeleteEndpointRequest;
import com.amazonaws.services.sns.model.GetEndpointAttributesResult;
import com.amazonaws.services.sns.model.SetEndpointAttributesRequest;
import com.google.common.collect.Maps;

/**
 * This interacts with SNS and to test against SNS itself, we'd need to create client application 
 * registrations across all environments. Using mocks for this DAO test instead (DynamoDB is a 
 * known system at this point so mock tests are OK).
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamoNotificationRegistrationDaoTest {

    private static final String PLATFORM_ARN = "platformARN";
    private static final String GUID = "registrationGuid";
    private static final String HEALTH_CODE = "healthCode";
    private static final String PUSH_NOTIFICATION_ENDPOINT_ARN = "endpoint";
    private static final String PHONE_ENDPOINT = "+14255550123";
    private static final String DEVICE_ID = "deviceId";
    private static final String OS_NAME = "osName";
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
        mockQuery(TestUtils.getNotificationRegistration(), TestUtils.getNotificationRegistration());

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(2, list.size());
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(HEALTH_CODE, query.getHashKeyValues().getHealthCode());
    }
    
    // Opted here to return an empty list, as it's a list operation and we're not asking for specific registration
    @Test
    public void listWhenNoneExist() {
        mockQuery();

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(0, list.size());
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(HEALTH_CODE, query.getHashKeyValues().getHealthCode());
    }
    
    @Test
    public void createPushNotification() {
        // No existing record
        mockQuery();

        doReturn(PUSH_NOTIFICATION_ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        NotificationRegistration result = dao.createPushNotificationRegistration(PLATFORM_ARN, registration);
        
        verify(mockSnsClient).createPlatformEndpoint(createPlatformEndpointRequestCaptor.capture());
        
        CreatePlatformEndpointRequest snsRequest = createPlatformEndpointRequestCaptor.getValue();
        assertEquals(PLATFORM_ARN, snsRequest.getPlatformApplicationArn());
        assertEquals(DEVICE_ID, snsRequest.getToken());
        assertNull(snsRequest.getCustomUserData());
        
        assertNotNull(result.getGuid());
        assertNotEquals(GUID, result.getGuid());
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        
        NotificationRegistration reg = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, reg.getHealthCode());
        assertEquals(result.getGuid(), reg.getGuid());
        assertEquals(PUSH_NOTIFICATION_ENDPOINT_ARN, reg.getEndpoint());
        assertEquals(DEVICE_ID, reg.getDeviceId());
        assertEquals(OperatingSystem.IOS, reg.getOsName());
        assertTrue(reg.getCreatedOn() > 0L);
        assertTrue(reg.getModifiedOn() > 0L);
    }
    
    // In this case, we want to see the GUID returned and no duplicate record created. So 
    // that's the only part that's additional to the createNotifications() test when new.
    @Test
    public void createPushNotificationWhenItExists() {
        mockQuery(getNotificationRegistration());

        doReturn(PUSH_NOTIFICATION_ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        NotificationRegistration result = dao.createPushNotificationRegistration(PLATFORM_ARN, registration);
        
        assertEquals(GUID, result.getGuid());
        
        // The save needs to use the same GUID and HEALTH_CODE or it'll be a duplicate
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration reg = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, reg.getHealthCode());
        assertEquals(GUID, reg.getGuid());
    }

    @Test
    public void createSmsRegistration() {
        // No existing record.
        mockQuery();

        // Execute.
        NotificationRegistration result = dao.createRegistration(getSmsNotificationRegistration());

        // Validate saved registration.
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration savedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, savedRegistration.getHealthCode());
        assertEquals(NotificationProtocol.SMS, savedRegistration.getProtocol());
        assertEquals(PHONE_ENDPOINT, savedRegistration.getEndpoint());
        assertNotEquals(GUID, savedRegistration.getGuid());
        assertTrue(savedRegistration.getCreatedOn() > 0L);
        assertNotEquals(CREATED_ON, savedRegistration.getCreatedOn());
        assertTrue(savedRegistration.getModifiedOn() > 0L);

        assertSame(result, savedRegistration);
    }

    @Test
    public void createSmsRegistrationAlreadyExists() {
        // Create existing record.
        NotificationRegistration existingRegistration = getSmsNotificationRegistration();
        existingRegistration.setGuid(GUID);
        existingRegistration.setCreatedOn(CREATED_ON);
        mockQuery(existingRegistration);

        // Execute.
        NotificationRegistration result = dao.createRegistration(getSmsNotificationRegistration());

        // Validate saved registration.
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration savedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, savedRegistration.getHealthCode());
        assertEquals(NotificationProtocol.SMS, savedRegistration.getProtocol());
        assertEquals(PHONE_ENDPOINT, savedRegistration.getEndpoint());
        assertEquals(GUID, savedRegistration.getGuid());
        assertEquals(CREATED_ON, savedRegistration.getCreatedOn());
        assertTrue(savedRegistration.getModifiedOn() > 0L);

        assertSame(result, savedRegistration);
    }

    @Test
    public void get() {
        NotificationRegistration registration = getNotificationRegistration();
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

    @Test(expected = EntityNotFoundException.class)
    public void updateNotExists() {
        // Mock mapper.
        when(mockMapper.load(any())).thenReturn(null);

        // Execute. Will throw.
        dao.updateRegistration(getNotificationRegistration());
    }

    @Test
    public void updateSmsNotificationDoesNothing() {
        // Mock mapper.
        NotificationRegistration existingRegistration = getSmsNotificationRegistration();
        existingRegistration.setGuid(GUID);
        when(mockMapper.load(any())).thenReturn(existingRegistration);

        // Execute.
        NotificationRegistration registrationToUpdate = getSmsNotificationRegistration();
        registrationToUpdate.setGuid(GUID);
        NotificationRegistration result = dao.updateRegistration(registrationToUpdate);
        assertSame(existingRegistration, result);

        // No back-ends called.
        verifyZeroInteractions(mockSnsClient);
        verify(mockMapper, never()).save(any());
    }

    @Test
    public void updateNoChange() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", DEVICE_ID);
        map.put("Enabled", "true");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        NotificationRegistration result = dao.updateRegistration(registration);
        assertSame(registration, result);
        
        verify(mockSnsClient, never()).setEndpointAttributes(any());
        verify(mockMapper, never()).save(any());
    }
    
    @Test
    public void updateWhenTokenHasChanged() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", "this is not the device id");
        map.put("Enabled", "false");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        dao.updateRegistration(registration);
        
        verify(mockSnsClient).setEndpointAttributes(setEndpointAttributesRequestCaptor.capture());
        SetEndpointAttributesRequest request = setEndpointAttributesRequestCaptor.getValue();
        assertEquals(registration.getEndpoint(), request.getEndpointArn());

        Map<String,String> attributes = request.getAttributes();
        assertEquals(DEVICE_ID, attributes.get("Token"));
        assertEquals("true", attributes.get("Enabled"));
        assertNull(attributes.get("CustomUserData"));
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration persisted = notificationRegistrationCaptor.getValue();
        assertEquals(GUID, persisted.getGuid());
        assertEquals(HEALTH_CODE, persisted.getHealthCode());
        assertEquals(PUSH_NOTIFICATION_ENDPOINT_ARN, persisted.getEndpoint());
        assertEquals(DEVICE_ID, persisted.getDeviceId());
        assertEquals(OS_NAME, persisted.getOsName());
        assertEquals(CREATED_ON, persisted.getCreatedOn());
        assertNotEquals(MODIFIED_ON, persisted.getModifiedOn()); // modified is changed
        assertTrue(persisted.getModifiedOn() > 0L);
    }
    
    @Test
    public void updateWhenNew() {
        doReturn(null).when(mockMapper).load(any());
        
        NotificationRegistration registration = getNotificationRegistration();
        try {
            dao.updateRegistration(registration);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }
    
    @Test
    public void delete() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        dao.deleteRegistration(HEALTH_CODE, GUID);
        
        verify(mockMapper).delete(notificationRegistrationCaptor.capture());
        NotificationRegistration del = notificationRegistrationCaptor.getValue();
        assertEquals(GUID, del.getGuid());
        assertEquals(HEALTH_CODE, del.getHealthCode());
        
        verify(mockSnsClient).deleteEndpoint(deleteEndpointRequestCaptor.capture());
        DeleteEndpointRequest request = deleteEndpointRequestCaptor.getValue();
        assertEquals(PUSH_NOTIFICATION_ENDPOINT_ARN, request.getEndpointArn());
    }
    
    @Test
    public void deleteWhenDoesNotExist() {
        doReturn(null).when(mockMapper).load(any());
        
        try {
            dao.deleteRegistration(HEALTH_CODE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }

    @Test
    public void deleteSmsNotification() {
        // Set up mocks.
        NotificationRegistration registration = getSmsNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());

        // Execute.
        dao.deleteRegistration(HEALTH_CODE, GUID);

        // Verify DDB mapper.
        verify(mockMapper).delete(notificationRegistrationCaptor.capture());
        NotificationRegistration deletedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(HEALTH_CODE, deletedRegistration.getHealthCode());
        assertEquals(NotificationProtocol.SMS, deletedRegistration.getProtocol());
        assertEquals(PHONE_ENDPOINT, deletedRegistration.getEndpoint());

        // Verify we don't call SNS delete endpoint for SMS registrations.
        verify(mockSnsClient, never()).deleteEndpoint(any());
    }

    private void mockQuery(NotificationRegistration... registrations) {
        List<NotificationRegistration> registrationList = ImmutableList.copyOf(registrations);
        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class),
                queryCaptor.capture());
        doReturn(registrationList.stream()).when(paginatedQueryList).stream();
    }

    private static NotificationRegistration getSmsNotificationRegistration() {
        NotificationRegistration existingRegistration = NotificationRegistration.create();
        existingRegistration.setHealthCode(HEALTH_CODE);
        existingRegistration.setProtocol(NotificationProtocol.SMS);
        existingRegistration.setEndpoint(PHONE_ENDPOINT);
        return existingRegistration;
    }
}
