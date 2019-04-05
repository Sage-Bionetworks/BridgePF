package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.when;
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
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.InvalidParameterException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class NotificationsServiceTest {
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String USER_ID = "user-id";
    private static final String HEALTH_CODE = "ABC";
    private static final String GUID = "ABC-DEF-GHI-JKL";
    private static final String OS_NAME = "iPhone OS";
    private static final String PLATFORM_ARN = "arn:platform";

    private static final CriteriaContext DUMMY_CONTEXT = new CriteriaContext.Builder().withStudyIdentifier(STUDY_ID)
            .withUserId(USER_ID).build();

    @Mock
    private NotificationTopicService mockNotificationTopicService;

    @Mock
    private ParticipantService mockParticipantService;

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
        service.setNotificationTopicService(mockNotificationTopicService);
        service.setParticipantService(mockParticipantService);
        service.setStudyService(mockStudyService);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        service.setSnsClient(mockSnsClient);

        Map<String,String> map = Maps.newHashMap();
        map.put(OS_NAME, PLATFORM_ARN);
        doReturn(map).when(mockStudy).getPushNotificationARNs();
     
        doReturn(mockStudy).when(mockStudyService).getStudy(STUDY_ID);
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
    public void createRegistration_PushNotification() {
        // Mock registration DAO.
        NotificationRegistration registration = getNotificationRegistration();
        registration.setHealthCode(HEALTH_CODE);
        registration.setOsName(OS_NAME);
        doReturn(registration).when(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);

        // Execute and validate.
        NotificationRegistration result = service.createRegistration(STUDY_ID, DUMMY_CONTEXT, registration);
        verify(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);
        assertEquals(registration, result);

        // We also manage criteria-based topics.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(STUDY_ID, DUMMY_CONTEXT, HEALTH_CODE);
    }

    @Test
    public void createRegistration_SmsNotification() {
        // Mock registration DAO.
        NotificationRegistration registration = getSmsNotificationRegistration();
        when(mockRegistrationDao.createRegistration(registration)).thenReturn(registration);

        // Mock participant DAO w/ phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(true).build();
        when(mockParticipantService.getParticipant(mockStudy, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        NotificationRegistration result = service.createRegistration(STUDY_ID, DUMMY_CONTEXT, registration);
        verify(mockRegistrationDao).createRegistration(registration);
        assertEquals(registration, result);

        // We also manage criteria-based topics.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(STUDY_ID, DUMMY_CONTEXT, HEALTH_CODE);
    }

    @Test(expected = BadRequestException.class)
    public void createRegistration_SmsNotificationPhoneNotVerified() {
        // Mock participant DAO w/ unverified phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(null).build();
        when(mockParticipantService.getParticipant(mockStudy, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        service.createRegistration(STUDY_ID, DUMMY_CONTEXT, getSmsNotificationRegistration());
    }

    @Test(expected = BadRequestException.class)
    public void createRegistration_SmsNotificationPhoneDoesNotMatch() {
        // Mock participant DAO w/ wrong phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(true).build();
        when(mockParticipantService.getParticipant(mockStudy, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        NotificationRegistration registration = getSmsNotificationRegistration();
        registration.setEndpoint("+14255550123");
        service.createRegistration(STUDY_ID, DUMMY_CONTEXT, registration);
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
    public void deleteAllRegistrations() {
        // Mock dependencies.
        NotificationRegistration pushNotificationRegistration = getNotificationRegistration();
        pushNotificationRegistration.setGuid("push-notification-registration");

        NotificationRegistration smsNotificationRegistration = getSmsNotificationRegistration();
        smsNotificationRegistration.setGuid("sms-notification-registration");

        when(mockRegistrationDao.listRegistrations(HEALTH_CODE)).thenReturn(ImmutableList.of(
                pushNotificationRegistration, smsNotificationRegistration));

        // Execute.
        service.deleteAllRegistrations(STUDY_ID, HEALTH_CODE);

        // Verify dependencies.
        verify(mockNotificationTopicService).unsubscribeAll(STUDY_ID, HEALTH_CODE,
                "push-notification-registration");
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, "push-notification-registration");

        verify(mockNotificationTopicService).unsubscribeAll(STUDY_ID, HEALTH_CODE,
                "sms-notification-registration");
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, "sms-notification-registration");
    }
    
    @Test
    public void deleteRegistration() {
        service.deleteRegistration(STUDY_ID, HEALTH_CODE, GUID);
        verify(mockNotificationTopicService).unsubscribeAll(STUDY_ID, HEALTH_CODE, GUID);
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, GUID);
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnCreate() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);

        NotificationRegistration result = service.createRegistration(STUDY_ID, DUMMY_CONTEXT, registration);
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

        service.createRegistration(STUDY_ID, DUMMY_CONTEXT, registration);
    }

    @Test
    public void sendNotificationOK() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setEndpoint("endpointARN");
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
    
    // Publish to two devices, where one device fails but the other sends to the user. 
    // Method succeeds but returns the GUID of the failed call for reporting back to the user.
    @Test
    public void sendNotificationWithPartialErrors() {
        NotificationRegistration reg1 = getNotificationRegistration();
        NotificationRegistration reg2 = getNotificationRegistration();
        reg2.setGuid("registrationGuid2");
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        when(mockSnsClient.publish(any()))
            .thenReturn(mockPublishResult)
            .thenThrow(new InvalidParameterException("bad parameter"));
        
        NotificationMessage message = getNotificationMessage();
        Set<String> erroredNotifications = service.sendNotificationToUser(STUDY_ID, HEALTH_CODE, message);
        assertEquals(1, erroredNotifications.size());
        assertEquals("registrationGuid2", Iterables.getFirst(erroredNotifications, null));        
    }
    
    // Publish to two devices, where all the devices fail. This should throw an exception as nothing 
    // was successfully returned to the user.
    @Test(expected = BadRequestException.class)
    public void sendNotificationAmazonExceptionConverted() {
        NotificationRegistration reg1 = getNotificationRegistration();
        NotificationRegistration reg2 = getNotificationRegistration();
        reg2.setGuid("registrationGuid2"); // This has to be different
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doThrow(new InvalidParameterException("bad parameter")).when(mockSnsClient).publish(any());
        
        NotificationMessage message = getNotificationMessage();
        service.sendNotificationToUser(STUDY_ID, HEALTH_CODE, message);
    }

    private static NotificationRegistration getSmsNotificationRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint(TestConstants.PHONE.getNumber());
        return registration;
    }
}
