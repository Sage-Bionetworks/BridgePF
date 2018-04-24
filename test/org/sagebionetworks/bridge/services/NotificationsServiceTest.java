package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestUtils.getNotificationMessage;
import static org.sagebionetworks.bridge.TestUtils.getNotificationRegistration;

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
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

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
    private static final String OS_NAME = "iPhone OS";
    private static final String PLATFORM_ARN = "arn:platform";
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Mock
    private PublishResult mockPublishResult;
    
    @Mock
    private NotificationRegistrationDao mockRegistrationDao;
    
    @Mock
    private Study mockStudy;
    
    @Captor
    private ArgumentCaptor<PublishRequest> requestCaptor;

    private NotificationsService service;
    
    @Before
    public void before() {
        service = new NotificationsService();
        service.setStudyService(mockStudyService);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        service.setSnsClient(mockSnsClient);
        
        Map<String,String> map = Maps.newHashMap();
        map.put(OS_NAME, PLATFORM_ARN);
        doReturn(map).when(mockStudy).getPushNotificationARNs();
     
        doReturn(mockStudy).when(mockStudyService).getStudy(STUDY_ID);
        doReturn(TestConstants.TEST_STUDY).when(mockStudy).getStudyIdentifier();
    }
    
    @Test
    public void listRegistrations() {
        List<NotificationRegistration> list = Lists.newArrayList(getNotificationRegistration());
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        List<NotificationRegistration> result = service.listRegistrations(HEALTH_CODE);
        
        verify(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        assertEquals(list, result);
    }
    
    @Test
    public void getRegistration() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        
        NotificationRegistration result = service.getRegistration(HEALTH_CODE, GUID);
        verify(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        assertEquals(registration, result);
    }
    
    @Test
    public void createRegistration() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName(OS_NAME);
        doReturn(registration).when(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        
        NotificationRegistration result = service.createRegistration(STUDY_ID, registration);
        verify(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        assertEquals(registration, result);
    }
    
    @Test
    public void updateRegistration() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName(OS_NAME);
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
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).createRegistration(PLATFORM_ARN, registration);
        
        NotificationRegistration result = service.createRegistration(STUDY_ID, registration);
        assertEquals(OperatingSystem.IOS, result.getOsName());
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnUpdate() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).updateRegistration(registration);
        
        NotificationRegistration result = service.updateRegistration(STUDY_ID, registration);
        assertEquals(OperatingSystem.IOS, result.getOsName());
    }
    
    @Test(expected = NotImplementedException.class)
    public void throwsUnimplementedExceptionIfPlatformHasNoARN() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName(OperatingSystem.ANDROID);
        
        service.createRegistration(STUDY_ID, registration);
    }

    @Test
    public void sendNotificationOK() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setEndpointARN("endpointARN");
        List<NotificationRegistration> list = Lists.newArrayList(registration);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doReturn(mockPublishResult).when(mockSnsClient).publish(any());
        
        NotificationMessage message = getNotificationMessage();
        
        service.sendNotificationToUser(STUDY_ID, HEALTH_CODE, message);
        
        verify(mockSnsClient).publish(requestCaptor.capture());
        
        PublishRequest request = requestCaptor.getValue();
        assertEquals(message.getSubject(), request.getSubject());
        assertEquals(message.getMessage(), request.getMessage());
        assertEquals("endpointARN", request.getTargetArn());
    }
    
    @Test
    public void sendNotificationNoRegistration() {
        doReturn(Lists.newArrayList()).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        NotificationMessage message = getNotificationMessage();
        try {
            service.sendNotificationToUser(STUDY_ID, HEALTH_CODE, message);
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
        NotificationRegistration reg1 = getNotificationRegistration();
        NotificationRegistration reg2 = getNotificationRegistration();
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doThrow(new InvalidParameterException("bad parameter")).when(mockSnsClient).publish(any());
        
        NotificationMessage message = getNotificationMessage();
        try {
            service.sendNotificationToUser(STUDY_ID, HEALTH_CODE, message);
            fail("Should have thrown exception.");
        } catch(BadRequestException e) {
            assertEquals("Error sending push notification: bad parameter; bad parameter.", e.getMessage());
        }
    }
    
    @Test
    public void sendTransactionalSMSMessageOK() throws Exception {
        doReturn(mockPublishResult).when(mockSnsClient).publish(any());
        
        String message = "This is my SMS message.";
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(mockStudy)
                .withSmsTemplate(new SmsTemplate(message))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();
        
        service.sendSmsMessage(provider);
        
        verify(mockSnsClient).publish(requestCaptor.capture());
        
        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(message, request.getMessage());
        assertEquals("Transactional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals("Bridge", 
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());
    }
    
    @Test
    public void sendPromotionalSMSMessageOK() throws Exception {
        doReturn(mockPublishResult).when(mockSnsClient).publish(any());
        
        String message = "This is my SMS message.";
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(mockStudy)
                .withSmsTemplate(new SmsTemplate(message))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        
        service.sendSmsMessage(provider);
        
        verify(mockSnsClient).publish(requestCaptor.capture());
        
        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(message, request.getMessage());
        assertEquals("Promotional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals("Bridge", 
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());
    }
    
    @Test(expected = BridgeServiceException.class)
    public void sendSMSMessageTooLongInvalid() throws Exception {
        doReturn(mockPublishResult).when(mockSnsClient).publish(any());
        String message = "This is my SMS message.";
        for (int i=0; i < 3; i++) {
            message += message;
        }
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(mockStudy)
                .withSmsTemplate(new SmsTemplate(message))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();
        
        service.sendSmsMessage(provider);
    }
}
