package org.sagebionetworks.bridge.services;

import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityEventServiceTest {

    private ActivityEventService activityEventService;

    private ActivityEventDao activityEventDao;
    
    @Before
    public void before() {
        activityEventDao = mock(ActivityEventDao.class);

        activityEventService = new ActivityEventService();
        activityEventService.setActivityEventDao(activityEventDao);
    }

    @Test
    public void canPublishCustomEvent() throws Exception {
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("eventKey1", "eventKey2"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        doNothing().when(activityEventDao).publishEvent(activityEventArgumentCaptor.capture(), eq(true));

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(study, "healthCode", "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals("custom:eventKey1", activityEvent.getEventId());
        assertEquals("healthCode", activityEvent.getHealthCode());
        assertEquals(timestamp.getMillis(), activityEvent.getTimestamp().longValue());
    }

    @Test
    public void canPublishCustomEventFromAutomaticEvents() {
        Study study = Study.create();
        study.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        doNothing().when(activityEventDao).publishEvent(activityEventArgumentCaptor.capture(), eq(true));

        DateTime timestamp = DateTime.now().plusDays(3);
        activityEventService.publishCustomEvent(study, "healthCode", "3-days-after-enrollment",
                timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals("custom:3-days-after-enrollment", activityEvent.getEventId());
        assertEquals("healthCode", activityEvent.getHealthCode());
        assertEquals(timestamp.getMillis(), activityEvent.getTimestamp().longValue());
    }

    @Test
    public void cannotPublishUnknownCustomEvent() throws Exception {
        Study study = Study.create();
        try {
            activityEventService.publishCustomEvent(study, "healthCode", "eventKey5",
                    DateTime.now());
            fail("expected exception");
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), endsWith("eventKey5"));
        }
    }
    
    @Test
    public void canPublishEvent() {
        ActivityEvent event = new Builder().withHealthCode("BBB")
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(DateTime.now()).build();

        activityEventService.publishActivityEvent(event, true);
        
        verify(activityEventDao).publishEvent(eq(event), eq(true));
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canGetActivityEventMap() {
        DateTime now = DateTime.now();
        
        Map<String,DateTime> map = Maps.newHashMap();
        map.put("enrollment", now);
        when(activityEventDao.getActivityEventMap("BBB")).thenReturn(map);

        Map<String, DateTime> results = activityEventService.getActivityEventMap("BBB");
        assertEquals(now, results.get("enrollment"));
        assertEquals(1, results.size());
        
        verify(activityEventDao).getActivityEventMap("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canDeleteActivityEvents() {
        activityEventService.deleteActivityEvents("BBB");
        
        verify(activityEventDao).deleteActivityEvents("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void badPublicDoesntCallDao() {
        try {
            activityEventService.publishActivityEvent((ActivityEvent) null, true);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            activityEventService.getActivityEventMap(null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            activityEventService.deleteActivityEvents(null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void canPublishEnrollmentEvent() {
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(Study.create(),"AAA-BBB-CCC", signature);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture(), eq(true));
        
        assertEquals("enrollment", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("AAA-BBB-CCC", argument.getValue().getHealthCode());
    }

    @Test
    public void canPublishEnrollmentEventWithAutomaticCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder().put("3-days-after", "P3D")
                .put("1-week-after", "P1W").put("13-weeks-after", "P13W").build());

        // Create consent signature
        DateTime enrollment = DateTime.parse("2018-04-04T16:00-0700");
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(enrollment.minusDays(10).getMillis())
                .withSignedOn(enrollment.getMillis()).build();

        // Execute
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", signature);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture(), eq(true));

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals("enrollment", publishedEventList.get(0).getEventId());
        assertEquals(enrollment.getMillis(), publishedEventList.get(0).getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(0).getHealthCode());

        assertEquals("custom:3-days-after", publishedEventList.get(1).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"), publishedEventList.get(1)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(1).getHealthCode());

        assertEquals("custom:1-week-after", publishedEventList.get(2).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"), publishedEventList.get(2)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(2).getHealthCode());

        assertEquals("custom:13-weeks-after", publishedEventList.get(3).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-07-04T16:00-0700"), publishedEventList.get(3)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(3).getHealthCode());
    }

    @Test
    public void canPublishSurveyAnswer() {
        DateTime now = DateTime.now();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnsweredOn(now.getMillis());
        answer.setQuestionGuid("BBB-CCC-DDD");
        answer.setAnswers(Lists.newArrayList("belgium"));

        activityEventService.publishQuestionAnsweredEvent("healthCode", answer);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture(), eq(true));
        
        assertEquals("question:BBB-CCC-DDD:answered", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("healthCode", argument.getValue().getHealthCode());
    }
    
    @Test
    public void doesNotPublishActivityFinishedEventForOldActivity() {
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setGuid("AAA");

        activityEventService.publishActivityFinishedEvent(activity);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canPublishActivityFinishedEvents() {
        long finishedOn = DateTime.now().getMillis();
        
        ScheduledActivity schActivity = ScheduledActivity.create();
        schActivity.setGuid("AAA:"+DateTime.now().toLocalDateTime());
        schActivity.setActivity(TestUtils.getActivity1());
        schActivity.setLocalExpiresOn(LocalDateTime.now().plusDays(1));
        schActivity.setStartedOn(DateTime.now().getMillis());
        schActivity.setFinishedOn(finishedOn);
        schActivity.setHealthCode("BBB");

        activityEventService.publishActivityFinishedEvent(schActivity);
        ArgumentCaptor<ActivityEvent> eventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(eventCaptor.capture(), eq(true));

        ActivityEvent event = eventCaptor.getValue();
        assertEquals("BBB", event.getHealthCode());
        assertEquals("activity:AAA:finished", event.getEventId());
        assertEquals(finishedOn, event.getTimestamp().longValue());
    }
    
    @Test
    public void deleteActivityEvent() {
        activityEventService.deleteActivityEvent("BBB", "eventId");
        verify(activityEventDao).deleteActivityEvent("BBB", "eventId");
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteActivityEventNoEventID() {
        activityEventService.deleteActivityEvent("BBB", null);
    }
}
