package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.services.NotificationTopicService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicControllerTest {

    private static final String GUID = "DEF-GHI";
    private static final String HEALTH_CODE = "healthCode";

    @Spy
    private NotificationTopicController controller;

    @Mock
    private NotificationTopicService mockTopicService;

    @Mock
    private UserSession mockUserSession;
    
    @Captor
    private ArgumentCaptor<NotificationTopic> topicCaptor;

    @Captor
    private ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @Captor
    private ArgumentCaptor<SubscriptionRequest> subRequestCaptor; 

    @Before
    public void before() throws Exception {
        this.controller.setNotificationTopicService(mockTopicService);

        doReturn(TEST_STUDY).when(mockUserSession).getStudyIdentifier();

        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
    }

    @Test
    public void getAllTopics() throws Exception {
        mockPlayContext();
        NotificationTopic topic = getNotificationTopic();
        doReturn(Lists.newArrayList(topic)).when(mockTopicService).listTopics(TEST_STUDY);

        Result result = controller.getAllTopics();
        assertEquals(200, result.status());

        JsonNode node = getResultNode(result);
        assertEquals(1, node.get("total").asInt());
        assertEquals("ResourceList", node.get("type").asText());

        ResourceList<NotificationTopic> topics = getTopicList(result);
        assertEquals(1, topics.getItems().size());
        assertEquals(topic.getGuid(), topics.getItems().get(0).getGuid());
    }

    @Test
    public void createTopic() throws Exception {
        NotificationTopic topic = getNotificationTopic();
        mockPlayContextWithJson(topic);
        doReturn(topic).when(mockTopicService).createTopic(any());

        Result result = controller.createTopic();

        JsonNode node = getResultNode(result);
        assertEquals(201, result.status());
        assertEquals("ABC-DEF", node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());

        verify(mockTopicService).createTopic(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals(topic.getName(), captured.getName());
    }

    @Test
    public void getTopic() throws Exception {
        mockPlayContext();
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicService).getTopic(TEST_STUDY, GUID);

        Result result = controller.getTopic(GUID);
        assertEquals(200, result.status());
        JsonNode node = getResultNode(result);
        assertEquals("NotificationTopic", node.get("type").asText());

        NotificationTopic returned = getTopic(result);
        assertEquals("Test Topic Name", returned.getName());
        assertEquals("ABC-DEF", returned.getGuid());
        assertNull(returned.getStudyId());
        assertNull(returned.getTopicARN());
    }

    @Test
    public void updateTopic() throws Exception {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicService).updateTopic(any());
        mockPlayContextWithJson(topic);

        Result result = controller.updateTopic(GUID);

        JsonNode node = getResultNode(result);
        assertEquals("GuidHolder", node.get("type").asText());

        verify(mockTopicService).updateTopic(topicCaptor.capture());
        NotificationTopic returned = topicCaptor.getValue();
        assertEquals(topic.getName(), returned.getName());
        assertEquals(GUID, returned.getGuid());
    }

    @Test
    public void deleteTopic() {
        Result result = controller.deleteTopic(GUID);
        assertEquals(200, result.status());

        verify(mockTopicService).deleteTopic(TEST_STUDY, GUID);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void cannotSendMessageAsDeveloper() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.getNotificationMessage());

        controller.sendNotification(GUID);
    }

    @Test
    public void sendNotification() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(RESEARCHER);

        NotificationMessage message = TestUtils.getNotificationMessage();
        TestUtils.mockPlayContextWithJson(message);

        Result result = controller.sendNotification(GUID);
        assertEquals(202, result.status());

        verify(mockTopicService).sendNotification(eq(TEST_STUDY), eq(GUID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals("a subject", captured.getSubject());
        assertEquals("a message", captured.getMessage());
    }

    @Test
    public void getSubscriptionStatuses() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(HEALTH_CODE).when(mockUserSession).getHealthCode();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(Lists.newArrayList(status)).when(mockTopicService)
                .currentSubscriptionStatuses(TEST_STUDY, HEALTH_CODE, "registrationGuid");
        TestUtils.mockPlayContextWithJson(TestUtils.getSubscriptionRequest());

        Result result = controller.getSubscriptionStatuses();
        assertEquals(200, result.status());
        
        ResourceList<SubscriptionStatus> statuses = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<SubscriptionStatus>>() {});
        assertEquals(1, statuses.getTotal());
        SubscriptionStatus retrievedStatus = statuses.getItems().get(0);
        assertEquals("topicGuid", retrievedStatus.getTopicGuid());
        assertEquals("topicName", retrievedStatus.getTopicName());
        assertTrue(retrievedStatus.isSubscribed());
    }

    @Test
    public void subscribe() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(HEALTH_CODE).when(mockUserSession).getHealthCode();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(Lists.newArrayList(status)).when(mockTopicService).subscribe(eq(TEST_STUDY), eq(HEALTH_CODE), any());
        TestUtils.mockPlayContextWithJson(TestUtils.getSubscriptionRequest());
        
        Result result = controller.subscribe();
        assertEquals(200, result.status());
        
        ResourceList<SubscriptionStatus> statuses = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<SubscriptionStatus>>() {});
        assertEquals(1, statuses.getTotal());
        SubscriptionStatus retrievedStatus = statuses.getItems().get(0);
        assertEquals("topicGuid", retrievedStatus.getTopicGuid());
        assertEquals("topicName", retrievedStatus.getTopicName());
        assertTrue(retrievedStatus.isSubscribed());
        
        verify(mockTopicService).subscribe(eq(TEST_STUDY), eq(HEALTH_CODE), subRequestCaptor.capture());
        
        SubscriptionRequest request = subRequestCaptor.getValue();
        assertEquals("registrationGuid", request.getRegistrationGuid());
        assertEquals(Sets.newHashSet("topicA", "topicB"), request.getTopicGuids());
    }

    // Test permissions of all the methods... DEVELOPER or DEVELOPER RESEARCHER. Do
    // something that

    private JsonNode getResultNode(Result result) throws Exception {
        return BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
    }

    private NotificationTopic getTopic(Result result) throws Exception {
        return BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), NotificationTopic.class);
    }

    private ResourceList<NotificationTopic> getTopicList(Result result) throws Exception {
        return BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                new TypeReference<ResourceList<NotificationTopic>>() {
                });
    }
}
