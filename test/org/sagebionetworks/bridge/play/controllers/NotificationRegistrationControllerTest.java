package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.NotificationsService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class NotificationRegistrationControllerTest {

    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String OS_NAME = "Android";
    private static final String DEVICE_ID = "deviceId";
    private static final String HEALTH_CODE = "healthCode";
    private static final String GUID = "ABC-DEF";
    private static final DateTime CREATED_ON = DateTime.now();
    private static final DateTime MODIFIED_ON = DateTime.now();
    
    @Mock
    private NotificationsService mockService;
    
    @Mock
    private UserSession session;
    
    @Spy
    private NotificationRegistrationController controller;
    
    @Captor
    private ArgumentCaptor<NotificationRegistration> registrationCaptor;
    
    @Before
    public void before() throws Exception {
        controller.setNotificationService(mockService);
        
        doReturn(HEALTH_CODE).when(session).getHealthCode();
        doReturn(true).when(session).doesConsent();
        doReturn(STUDY_ID).when(session).getStudyIdentifier();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        TestUtils.mockPlayContext();
    }
    
    private List<NotificationRegistration> createRegList() {
        NotificationRegistration reg = NotificationRegistration.create();
        reg.setOsName(OS_NAME);
        reg.setGuid(GUID);
        reg.setHealthCode(HEALTH_CODE);
        reg.setEndpointARN("endpointARN");
        reg.setDeviceId(DEVICE_ID);
        reg.setCreatedOn(CREATED_ON.getMillis());
        reg.setModifiedOn(MODIFIED_ON.getMillis());
        
        return Lists.newArrayList(reg);
    }
    
    @Test
    public void getAllRegistrations() throws Exception {
        doReturn(createRegList()).when(mockService).listRegistrations(HEALTH_CODE);
        
        Result result = controller.getAllRegistrations();
        
        verify(mockService).listRegistrations(HEALTH_CODE);
        
        assertEquals(200, result.status());
        ResourceList<NotificationRegistration> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<NotificationRegistration>>() {});

        assertEquals(1, list.getTotal());
        assertEquals(1, list.getItems().size());
        
        NotificationRegistration registration = list.getItems().get(0);
        verifyRegistration(registration);
    }
        
    @Test
    public void createRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockService).createRegistration(any(), any());
        
        String json = TestUtils.createJson("{'deviceId':'"+DEVICE_ID+"','osName':'"+OS_NAME+"'}");
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.createRegistration();
        
        assertEquals(201, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(GUID, node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());
        
        verify(mockService).createRegistration(eq(STUDY_ID), registrationCaptor.capture());
        
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(DEVICE_ID, registration.getDeviceId());
        assertEquals(OS_NAME, registration.getOsName());
        assertEquals(HEALTH_CODE, registration.getHealthCode());
    }
    
    @Test
    public void updateRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockService).updateRegistration(any(), any());
        
        String json = TestUtils.createJson("{'guid':'guidWeIgnore','deviceId':'NEW_DEVICE_ID','osName':'"+OS_NAME+"'}");
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.updateRegistration(GUID);
        
        assertEquals(200, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(GUID, node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());
        
        verify(mockService).updateRegistration(eq(STUDY_ID), registrationCaptor.capture());
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals("NEW_DEVICE_ID", registration.getDeviceId());
        assertEquals(OS_NAME, registration.getOsName());
        assertEquals(HEALTH_CODE, registration.getHealthCode());
        assertEquals(GUID, registration.getGuid());
    }
    
    @Test
    public void getRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockService).getRegistration(HEALTH_CODE, GUID);
        
        Result result = controller.getRegistration(GUID);
        
        verify(mockService).getRegistration(HEALTH_CODE, GUID);
        
        assertEquals(200, result.status());
        NotificationRegistration registration = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                NotificationRegistration.class);
        verifyRegistration(registration);
    }
    
    @Test
    public void deleteRegistration() throws Exception {
        Result result = controller.deleteRegistration(GUID);
        
        verify(mockService).deleteRegistration(HEALTH_CODE, GUID);
        TestUtils.assertResult(result, 200, "Push notification registration deleted.");
    }

    private void verifyRegistration(NotificationRegistration reg) {
        assertNull(reg.getHealthCode());
        assertNull(reg.getEndpointARN());
        assertEquals(OS_NAME, reg.getOsName());
        assertEquals(GUID, reg.getGuid());
        assertEquals(DEVICE_ID, reg.getDeviceId());
        assertEquals(CREATED_ON.getMillis(), reg.getCreatedOn());
        assertEquals(MODIFIED_ON.getMillis(), reg.getModifiedOn());
    }
}
