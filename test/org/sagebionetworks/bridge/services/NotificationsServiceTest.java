package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.InvalidParameterException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class NotificationsServiceTest {
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String HEALTH_CODE = "ABC";
    private static final String GUID = "ABC-DEF-GHI-JKL";
    private static final String DEVICE_ID = "MNO-PQR-STU-VWX";
    private static final String OS_NAME = "iPhone OS";
    private static final String PLATFORM_ARN = "arn:platform";
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AmazonSNSClient snsClient;
    
    @Mock
    private PublishResult publishResult;
    
    @Mock
    private NotificationRegistrationDao mockRegistrationDao;
    
    @Mock
    private Study study;
    
    @Captor
    private ArgumentCaptor<PublishRequest> requestCaptor;

    private NotificationsService service;
    
    @Before
    public void before() {
        service = new NotificationsService();
        service.setStudyService(mockStudyService);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        service.setSnsClient(snsClient);
        
        Map<String,String> map = Maps.newHashMap();
        map.put(OS_NAME, PLATFORM_ARN);
        doReturn(map).when(study).getPushNotificationARNs();
     
        doReturn(study).when(mockStudyService).getStudy(STUDY_ID);
    }
    
    private NotificationRegistration createRegistrationObject() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OS_NAME);
        registration.setHealthCode(HEALTH_CODE);
        return registration;
    }
    
    @Test
    public void listRegistrations() {
        List<NotificationRegistration> list = Lists.newArrayList(createRegistrationObject());
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        List<NotificationRegistration> result = service.listRegistrations(HEALTH_CODE);
        
        verify(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        assertEquals(list, result);
    }
    
    @Test
    public void getRegistration() {
        NotificationRegistration registration = createRegistrationObject();
        doReturn(registration).when(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        
        NotificationRegistration result = service.getRegistration(HEALTH_CODE, GUID);
        verify(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        assertEquals(registration, result);
    }
    
    @Test
    public void createRegistration() {
        NotificationRegistration registration = createRegistrationObject();
        doReturn(registration).when(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        
        NotificationRegistration result = service.createRegistration(STUDY_ID, registration);
        verify(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        assertEquals(registration, result);
    }
    
    @Test
    public void updateRegistration() {
        NotificationRegistration registration = createRegistrationObject();
        doReturn(registration).when(mockRegistrationDao).updateRegistration(registration);
        
        NotificationRegistration result = service.updateRegistration(STUDY_ID, registration);
        verify(mockRegistrationDao).updateRegistration(registration);
        assertEquals(registration, result);
    }
    
    @Test
    public void deleteRegistration() {
        service.deleteRegistration(HEALTH_CODE, GUID);
        
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, GUID);
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnCreate() {
        NotificationRegistration registration = createRegistrationObject();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        
        NotificationRegistration result = service.createRegistration(STUDY_ID, registration);
        assertEquals(OperatingSystem.IOS, result.getOsName());
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnUpdate() {
        NotificationRegistration registration = createRegistrationObject();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).updateRegistration(registration);
        
        NotificationRegistration result = service.updateRegistration(STUDY_ID, registration);
        assertEquals(OperatingSystem.IOS, result.getOsName());
    }
    
    @Test(expected = NotImplementedException.class)
    public void throwsUnimplementedExceptionIfPlatformHasNoARN() {
        NotificationRegistration registration = createRegistrationObject();
        registration.setOsName(OperatingSystem.ANDROID);
        
        service.createRegistration(STUDY_ID, registration);
    }

    @Test
    public void sendNotificationOK() {
        NotificationRegistration registration = createRegistrationObject();
        registration.setEndpointARN("endpointARN");
        List<NotificationRegistration> list = Lists.newArrayList(registration);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doReturn(publishResult).when(snsClient).publish(any());
        
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        service.sendNotification(STUDY_ID, HEALTH_CODE, message);
        
        verify(snsClient).publish(requestCaptor.capture());
        
        PublishRequest request = requestCaptor.getValue();
        assertEquals(message.getSubject(), request.getSubject());
        assertEquals(message.getMessage(), request.getMessage());
        assertEquals("endpointARN", request.getTargetArn());
    }
    
    @Test
    public void sendNotificationNoRegistration() {
        doReturn(Lists.newArrayList()).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        NotificationMessage message = TestUtils.getNotificationMessage();
        try {
            service.sendNotification(STUDY_ID, HEALTH_CODE, message);
            fail("Should have thrown exception.");
        } catch(BadRequestException e) {
            assertEquals("Participant has not registered to receive push notifications.", e.getMessage());
        }
    }
    
    // In this test, we create no less than two amazon exceptions, and verify that we throw one exception 
    // that summarizes the sorry situation for the user, while also hiding all the Amazon gobbledy-gook. 
    // This is thrown as a 400 error because the most common ways you can trigger it are to submit bad 
    // data, like an invalid device token. 
    @Test
    public void sendNotificationAmazonExceptionConverted() {
        NotificationRegistration reg1 = createRegistrationObject();
        NotificationRegistration reg2 = createRegistrationObject();
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doThrow(new InvalidParameterException("bad parameter")).when(snsClient).publish(any());
        
        NotificationMessage message = TestUtils.getNotificationMessage();
        try {
            service.sendNotification(STUDY_ID, HEALTH_CODE, message);
            fail("Should have thrown exception.");
        } catch(BadRequestException e) {
            assertEquals("Error sending push notification: bad parameter; bad parameter.", e.getMessage());
        }
    }
}
