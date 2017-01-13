package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

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
    private NotificationRegistrationDao mockRegistrationDao;
    
    @Mock
    private Study study;

    private NotificationsService service;
    
    @Before
    public void before() {
        service = new NotificationsService();
        service.setStudyService(mockStudyService);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        
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
}
