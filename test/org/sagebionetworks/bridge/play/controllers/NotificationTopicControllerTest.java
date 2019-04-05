package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.services.NotificationTopicService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicControllerTest {

    private static final String GUID = "DEF-GHI";

    @Spy
    private NotificationTopicController controller;

    @Mock
    private NotificationTopicService mockTopicService;
    
    @Mock
    private BridgeConfig mockBridgeConfig;

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
        controller.setBridgeConfig(mockBridgeConfig);
        
        doReturn(Environment.UAT).when(mockBridgeConfig).getEnvironment();

        doReturn(TEST_STUDY).when(mockUserSession).getStudyIdentifier();
    }

    @Test
    public void getAllTopicsIncludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        TestUtils.mockPlay().mock();
        NotificationTopic topic = getNotificationTopic();
        doReturn(Lists.newArrayList(topic)).when(mockTopicService).listTopics(TEST_STUDY, true);

        Result result = controller.getAllTopics("true");
        TestUtils.assertResult(result, 200);

        JsonNode node = getResultNode(result);
        assertEquals(1, node.get("items").size());
        assertEquals("ResourceList", node.get("type").asText());

        ResourceList<NotificationTopic> topics = getTopicList(result);
        assertEquals(1, topics.getItems().size());
        assertEquals(topic.getGuid(), topics.getItems().get(0).getGuid());
    }

    @Test
    public void getAllTopicsExcludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        TestUtils.mockPlay().mock();
        NotificationTopic topic = getNotificationTopic();
        doReturn(Lists.newArrayList(topic)).when(mockTopicService).listTopics(TEST_STUDY, false);

        Result result = controller.getAllTopics("false");
        TestUtils.assertResult(result, 200);

        // It's enough to test it's there, the prior test has a more complete test of the payload
        JsonNode node = getResultNode(result);
        assertEquals(1, node.get("items").size());
    }
    
    @Test
    public void createTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        TestUtils.mockPlay().withBody(topic).mock();
        doReturn(topic).when(mockTopicService).createTopic(any());

        Result result = controller.createTopic();
        TestUtils.assertResult(result, 201);
        
        JsonNode node = getResultNode(result);
        assertEquals("topicGuid", node.get("guid").asText());
        assertEquals("GuidHolder", node.get("type").asText());

        verify(mockTopicService).createTopic(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals(topic.getName(), captured.getName());
    }

    @Test
    public void getTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        TestUtils.mockPlay().mock();
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicService).getTopic(TEST_STUDY, GUID);

        Result result = controller.getTopic(GUID);
        TestUtils.assertResult(result, 200);
        
        JsonNode node = getResultNode(result);
        assertEquals("NotificationTopic", node.get("type").asText());

        NotificationTopic returned = getTopic(result);
        assertEquals("Test Topic Name", returned.getName());
        assertEquals("topicGuid", returned.getGuid());
        assertNull(returned.getStudyId());
        assertNull(returned.getTopicARN());
    }

    @Test
    public void updateTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicService).updateTopic(any());
        TestUtils.mockPlay().withBody(topic).mock();

        Result result = controller.updateTopic(GUID);
        TestUtils.assertResult(result, 200);

        JsonNode node = getResultNode(result);
        assertEquals("GuidHolder", node.get("type").asText());

        verify(mockTopicService).updateTopic(topicCaptor.capture());
        NotificationTopic returned = topicCaptor.getValue();
        assertEquals(topic.getName(), returned.getName());
        assertEquals(GUID, returned.getGuid());
    }
    
    @Test
    public void deleteTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);        
        Result result = controller.deleteTopic(GUID, "false");
        TestUtils.assertResult(result, 200);

        verify(mockTopicService).deleteTopic(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteTopicPermanently() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);        
        when(mockUserSession.isInRole(ADMIN)).thenReturn(true);
        
        Result result = controller.deleteTopic(GUID, "true");
        TestUtils.assertResult(result, 200);

        // Does not delete permanently because permissions are wrong; just logically deletes
        verify(mockTopicService).deleteTopicPermanently(TEST_STUDY, GUID);
    }

    @Test
    public void deleteTopicPermanentlyForDeveloper() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Result result = controller.deleteTopic(GUID, "true");
        TestUtils.assertResult(result, 200);

        // Does not delete permanently because permissions are wrong; just logically deletes
        verify(mockTopicService).deleteTopic(TEST_STUDY, GUID);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void cannotSendMessageAsDeveloper() throws Exception {
        TestUtils.mockPlay().withBody(TestUtils.getNotificationMessage()).mock();

        controller.sendNotification(GUID);
    }

    @Test
    public void sendNotification() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(ADMIN);

        NotificationMessage message = TestUtils.getNotificationMessage();
        TestUtils.mockPlay().withBody(message).mock();

        Result result = controller.sendNotification(GUID);
        TestUtils.assertResult(result, 202);

        verify(mockTopicService).sendNotification(eq(TEST_STUDY), eq(GUID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals("a subject", captured.getSubject());
        assertEquals("a message", captured.getMessage());
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
