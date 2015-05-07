package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent.Builder;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;

import com.google.common.collect.Lists;
import com.newrelic.agent.deps.com.google.common.collect.Maps;

public class TaskEventServiceTest {

    private TaskEventService service;
    
    private TaskEventDao taskEventDao;
    
    @Before
    public void before() {
        service = new TaskEventService();
        
        taskEventDao = mock(TaskEventDao.class);
        service.setTaskEventDao(taskEventDao);
    }
    
    @Test
    public void canPublishEvent() {
        TaskEvent event = new Builder().withHealthCode("BBB")
            .withType(TaskEventType.ENROLLMENT).withTimestamp(DateTime.now()).build();
        
        service.publishEvent(event);
        
        verify(taskEventDao).publishEvent(eq(event));
        verifyNoMoreInteractions(taskEventDao);
    }
    
    @Test
    public void canGetTaskEventMap() {
        DateTime now = DateTime.now();
        
        Map<String,DateTime> map = Maps.newHashMap();
        map.put("enrollment", now);
        when(taskEventDao.getTaskEventMap("BBB")).thenReturn(map);
        
        Map<String,DateTime> results = service.getTaskEventMap("BBB");
        assertEquals(now, results.get("enrollment"));
        assertEquals(1, results.size());
        
        verify(taskEventDao).getTaskEventMap("BBB");
        verifyNoMoreInteractions(taskEventDao);
    }
    
    @Test
    public void canDeleteTaskEvents() {
        service.deleteTaskEvents("BBB");
        
        verify(taskEventDao).deleteTaskEvents("BBB");
        verifyNoMoreInteractions(taskEventDao);
    }

    @Test
    public void badPublicDoesntCallDao() {
        try {
            service.publishEvent((TaskEvent)null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(taskEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            service.getTaskEventMap(null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(taskEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            service.deleteTaskEvents(null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(taskEventDao);
    }

    @Test
    public void canPublishConsent() {
        DateTime now = DateTime.now();
        
        DynamoUserConsent2 consent = new DynamoUserConsent2();
        consent.setConsentCreatedOn(now.minusDays(10).getMillis());
        consent.setHealthCode("AAA-BBB-CCC");
        consent.setSignedOn(now.getMillis());
        
        service.publishEvent(consent);
        
        ArgumentCaptor<TaskEvent> argument = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventDao).publishEvent(argument.capture());
        
        assertEquals("enrollment", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("AAA-BBB-CCC", argument.getValue().getHealthCode());
    }
    
    
    @Test
    public void canPublishSurveyAnswer() {
        DateTime now = DateTime.now();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnsweredOn(now.getMillis());
        answer.setQuestionGuid("BBB-CCC-DDD");
        answer.setAnswers(Lists.newArrayList("belgium"));
        
        service.publishEvent("healthCode", answer);
        
        ArgumentCaptor<TaskEvent> argument = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventDao).publishEvent(argument.capture());
        
        assertEquals("question:BBB-CCC-DDD:answered=belgium", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("healthCode", argument.getValue().getHealthCode());
    }

    @Test
    public void canPublishSurveyResponse() {
        DateTime now = DateTime.now();
        
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setCompletedOn(now.getMillis());
        response.setHealthCode("healthCode");
        response.setSurveyKey("BBB-CCC-DDD:123123123");
        
        service.publishEvent(response);
        
        ArgumentCaptor<TaskEvent> argument = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventDao).publishEvent(argument.capture());
        
        assertEquals("survey:BBB-CCC-DDD:finished", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("healthCode", argument.getValue().getHealthCode());
    }
    
    
}
