package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

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
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.NotificationsService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class NotificationRegistrationControllerTest {

    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String OS_NAME = "osName";
    private static final String DEVICE_ID = "deviceId";
    private static final String HEALTH_CODE = "healthCode";
    private static final String GUID = "registrationGuid";
    
    @Mock
    private NotificationsService mockNotificationService;
    
    @Mock
    private NotificationTopicService mockTopicService;
    
    @Mock
    private UserSession session;
    
    @Spy
    private NotificationRegistrationController controller;
    
    @Captor
    private ArgumentCaptor<NotificationRegistration> registrationCaptor;
    
    @Captor
    private ArgumentCaptor<SubscriptionRequest> subRequestCaptor;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    
    @Captor
    private ArgumentCaptor<Set<String>> stringSetCaptor;
    
    @Before
    public void before() throws Exception {
        controller.setNotificationService(mockNotificationService);
        controller.setNotificationTopicService(mockTopicService);
        
        doReturn(HEALTH_CODE).when(session).getHealthCode();
        doReturn(true).when(session).doesConsent();
        doReturn(STUDY_ID).when(session).getStudyIdentifier();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    }
    
    private List<NotificationRegistration> createRegList() {
        return Lists.newArrayList(TestUtils.getNotificationRegistration());
    }
    
    @Test
    public void getAllRegistrations() throws Exception {
        doReturn(createRegList()).when(mockNotificationService).listRegistrations(HEALTH_CODE);
        
        Result result = controller.getAllRegistrations();
        
        verify(mockNotificationService).listRegistrations(HEALTH_CODE);
        
        assertEquals(200, result.status());
        ResourceList<NotificationRegistration> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<NotificationRegistration>>() {});

        assertEquals(1, list.getItems().size());
        
        NotificationRegistration registration = list.getItems().get(0);
        assertEquals("deviceId", registration.getDeviceId());
        assertEquals("registrationGuid", registration.getGuid());
        assertEquals("osName", registration.getOsName());
        assertNull(registration.getEndpointARN());
        assertNull(registration.getHealthCode());
    }
        
    @Test
    public void createRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockNotificationService).createRegistration(any(), any());
        
        String json = TestUtils.createJson("{'deviceId':'"+DEVICE_ID+"','osName':'"+OS_NAME+"'}");
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.createRegistration();
        
        assertEquals(201, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(GUID, node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());
        
        verify(mockNotificationService).createRegistration(eq(STUDY_ID), registrationCaptor.capture());
        
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(DEVICE_ID, registration.getDeviceId());
        assertEquals(OS_NAME, registration.getOsName());
        assertEquals(HEALTH_CODE, registration.getHealthCode());
    }
    
    @Test
    public void updateRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockNotificationService).updateRegistration(any(), any());
        
        String json = TestUtils.createJson("{'guid':'guidWeIgnore','deviceId':'NEW_DEVICE_ID','osName':'"+OS_NAME+"'}");
        TestUtils.mockPlayContextWithJson(json);
        
        Result result = controller.updateRegistration(GUID);
        
        assertEquals(200, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(GUID, node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());
        
        verify(mockNotificationService).updateRegistration(eq(STUDY_ID), registrationCaptor.capture());
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals("NEW_DEVICE_ID", registration.getDeviceId());
        assertEquals(OS_NAME, registration.getOsName());
        assertEquals(HEALTH_CODE, registration.getHealthCode());
        assertEquals(GUID, registration.getGuid());
    }
    
    @Test
    public void getRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockNotificationService).getRegistration(HEALTH_CODE, GUID);
        
        Result result = controller.getRegistration(GUID);
        
        verify(mockNotificationService).getRegistration(HEALTH_CODE, GUID);
        
        assertEquals(200, result.status());
        NotificationRegistration registration = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                NotificationRegistration.class);
        verifyRegistration(registration);
    }
    
    @Test
    public void deleteRegistration() throws Exception {
        Result result = controller.deleteRegistration(GUID);
        
        verify(mockNotificationService).deleteRegistration(HEALTH_CODE, GUID);
        TestUtils.assertResult(result, 200, "Push notification registration deleted.");
    }

    @Test
    public void getSubscriptionStatuses() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(HEALTH_CODE).when(session).getHealthCode();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(Lists.newArrayList(status)).when(mockTopicService)
                .currentSubscriptionStatuses(STUDY_ID, HEALTH_CODE, GUID);
        TestUtils.mockPlayContext();

        Result result = controller.getSubscriptionStatuses(GUID);
        assertEquals(200, result.status());
        
        ResourceList<SubscriptionStatus> statuses = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<SubscriptionStatus>>() {});
        assertEquals(1, statuses.getItems().size());
        SubscriptionStatus retrievedStatus = statuses.getItems().get(0);
        assertEquals("topicGuid", retrievedStatus.getTopicGuid());
        assertEquals("topicName", retrievedStatus.getTopicName());
        assertTrue(retrievedStatus.isSubscribed());
    }

    @Test
    public void subscribe() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(HEALTH_CODE).when(session).getHealthCode();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(Lists.newArrayList(status)).when(mockTopicService).subscribe(eq(STUDY_ID), eq(HEALTH_CODE), eq(GUID), any());
        TestUtils.mockPlayContextWithJson(TestUtils.getSubscriptionRequest());
        
        Result result = controller.subscribe(GUID);
        assertEquals(200, result.status());
        
        ResourceList<SubscriptionStatus> statuses = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<SubscriptionStatus>>() {});
        assertEquals(1, statuses.getItems().size());
        SubscriptionStatus retrievedStatus = statuses.getItems().get(0);
        assertEquals("topicGuid", retrievedStatus.getTopicGuid());
        assertEquals("topicName", retrievedStatus.getTopicName());
        assertTrue(retrievedStatus.isSubscribed());
        
        verify(mockTopicService).subscribe(eq(STUDY_ID), eq(HEALTH_CODE), stringCaptor.capture(), stringSetCaptor.capture());
        
        assertEquals("registrationGuid", stringCaptor.getValue());
        assertEquals(Sets.newHashSet("topicA", "topicB"), stringSetCaptor.getValue());
    }


    private void verifyRegistration(NotificationRegistration reg) {
        assertNull(reg.getHealthCode());
        assertNull(reg.getEndpointARN());
        assertEquals(OS_NAME, reg.getOsName());
        assertEquals(GUID, reg.getGuid());
        assertEquals(DEVICE_ID, reg.getDeviceId());
        assertEquals(TestUtils.getNotificationRegistration().getCreatedOn(), reg.getCreatedOn());
    }
}
