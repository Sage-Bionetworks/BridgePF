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
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityEventServiceTest {

    private ActivityEventService service;
    
    private ActivityEventDao activityEventDao;
    
    @Before
    public void before() {
        service = new ActivityEventService();
        
        activityEventDao = mock(ActivityEventDao.class);
        service.setActivityEventDao(activityEventDao);
    }
    
    @Test
    public void canPublishEvent() {
        ActivityEvent event = new Builder().withHealthCode("BBB")
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(DateTime.now()).build();
        
        service.publishActivityEvent(event);
        
        verify(activityEventDao).publishEvent(eq(event));
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canGetActivityEventMap() {
        DateTime now = DateTime.now();
        
        Map<String,DateTime> map = Maps.newHashMap();
        map.put("enrollment", now);
        when(activityEventDao.getActivityEventMap("BBB")).thenReturn(map);
        
        Map<String,DateTime> results = service.getActivityEventMap("BBB");
        assertEquals(now, results.get("enrollment"));
        assertEquals(1, results.size());
        
        verify(activityEventDao).getActivityEventMap("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canDeleteActivityEvents() {
        service.deleteActivityEvents("BBB");
        
        verify(activityEventDao).deleteActivityEvents("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void badPublicDoesntCallDao() {
        try {
            service.publishActivityEvent((ActivityEvent)null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            service.getActivityEventMap(null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            service.deleteActivityEvents(null);    
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void canPublishConsent() {
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();
        
        service.publishEnrollmentEvent("AAA-BBB-CCC", signature);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
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
        
        service.publishQuestionAnsweredEvent("healthCode", answer);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals("question:BBB-CCC-DDD:answered", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("healthCode", argument.getValue().getHealthCode());
    }
    
    @Test
    public void doesNotPublishActivityFinishedEventForOldActivity() {
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setGuid("AAA");
        
        service.publishActivityFinishedEvent(activity);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canPublishActivityFinishedEvents() {
        long finishedOn = DateTime.now().getMillis();
        
        ScheduledActivity schActivity = ScheduledActivity.create();
        schActivity.setGuid("AAA:"+DateTime.now().toLocalDateTime());
        schActivity.setActivity(TestConstants.TEST_1_ACTIVITY);
        schActivity.setLocalExpiresOn(LocalDateTime.now().plusDays(1));
        schActivity.setStartedOn(DateTime.now().getMillis());
        schActivity.setFinishedOn(finishedOn);
        schActivity.setHealthCode("BBB");
        
        
        service.publishActivityFinishedEvent(schActivity);
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());

        ActivityEvent event = argument.getValue();
        assertEquals("BBB", event.getHealthCode());
        assertEquals("activity:AAA:finished", event.getEventId());
        assertEquals(finishedOn, event.getTimestamp().longValue());
    }
}
