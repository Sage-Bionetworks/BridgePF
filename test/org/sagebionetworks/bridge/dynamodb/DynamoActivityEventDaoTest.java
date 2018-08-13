package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoActivityEventDaoTest {

    private String healthCode;
    
    @Resource
    ActivityEventDao activityEventDao;
    
    @Before
    public void before() {
        healthCode = TestUtils.randomName(DynamoActivityEventDaoTest.class);
    }
    
    @Test
    public void canCrudEvent() {
        // Put all the initial times in non-UTC timezone, they should come back in map in UTC.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(4);
        DateTime time1 = DateTime.now().withZone(MSK);
        DateTime time2 = time1.plusDays(1).withZone(MSK);
        DateTime time3 = time1.plusDays(2).withZone(MSK);
        DateTime time4 = time1.plusDays(3).withZone(MSK);
        DateTime time5 = time1.plusDays(4).withZone(MSK);
        DateTime time6 = time1.plusDays(5).withZone(MSK);
        
        // This is an answer event. It's key should be "question:CCC:answered" with a value column
        // the activity event map should create a key with the value, such as "question:CCC:answered=value"
        ActivityEvent event = getEnrollmentEvent(time1);
        activityEventDao.publishEvent(event, true);
        event = getSurveyFinishedEvent(time2);
        activityEventDao.publishEvent(event, true);
        event = getQuestionAnsweredEvent(time3, "someValue");
        activityEventDao.publishEvent(event, true);
        event = getScheduledActivityFinishedEvent(time6);
        activityEventDao.publishEvent(event, true);
        
        Map<String,DateTime> map = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(6, map.size());
        assertEquals(time1.withZone(DateTimeZone.UTC), map.get("enrollment"));
        assertEquals(time1.withZone(DateTimeZone.UTC).minusWeeks(2), map.get("two_weeks_before_enrollment"));
        assertEquals(time1.withZone(DateTimeZone.UTC).minusMonths(2), map.get("two_months_before_enrollment"));
        assertEquals(time2.withZone(DateTimeZone.UTC), map.get("survey:AAA-BBB-CCC:finished"));
        assertEquals(time3.withZone(DateTimeZone.UTC), map.get("question:DDD-EEE-FFF:answered=someValue"));
        assertEquals(time6.withZone(DateTimeZone.UTC), map.get("activity:AAA-BBB-CCC:finished"));
        
        // Update timestamp of answer event while keeping same answer
        event = getQuestionAnsweredEvent(time4, "someValue");
        activityEventDao.publishEvent(event, true);
        
        map = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(time4.withZone(DateTimeZone.UTC), map.get("question:DDD-EEE-FFF:answered=someValue"));
        
        // Update answer event with different answer and later timestamp
        event = getQuestionAnsweredEvent(time5, "anotherAnswer");
        activityEventDao.publishEvent(event, true);
        
        // Creates a different key in activity event map. Researchers schedule against specific answers.
        map = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(time5.withZone(DateTimeZone.UTC), map.get("question:DDD-EEE-FFF:answered=anotherAnswer"));
        // The key point here is that the other answer is no longer in the map, so there can't be 
        // an "either or" scheduling conflict. The user can only answer one way or another on a 
        // given question, even if the answer is updated.
        assertNull(map.get("question:DDD-EEE-FFF:answered=someAnswer"));
        
        assertTrue(map.containsKey("enrollment"));
        activityEventDao.deleteActivityEvent(healthCode, "enrollment");
        map = activityEventDao.getActivityEventMap(healthCode);
        assertFalse(map.containsKey("enrollment"));
        
        activityEventDao.deleteActivityEvents(healthCode);
        
        map = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(0, map.size());
    }
    
    @Test
    public void ifNoEnrollmentNoCalculatedEvents() {
        Map<String,DateTime> map = activityEventDao.getActivityEventMap("not-a-health-code");
        assertTrue(map.isEmpty());
    }
    
    @Test
    public void neverUpdateEnrollmentTaskEvent() {
        final DateTime firstEvent = DateTime.now();
        
        ActivityEvent event = getEnrollmentEvent(firstEvent);
        activityEventDao.publishEvent(event, true);
        
        // This does not work. You can't do this.
        event = getEnrollmentEvent(firstEvent.plusHours(2));
        activityEventDao.publishEvent(event, true);
        
        Map<String,DateTime> eventMap = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(firstEvent.withZone(DateTimeZone.UTC), eventMap.get("enrollment"));
        
        // Now change the flag, and you can update the enrollment event
        activityEventDao.publishEvent(event, false);
        eventMap = activityEventDao.getActivityEventMap(healthCode);
        assertEquals(firstEvent.withZone(DateTimeZone.UTC).plusHours(2), eventMap.get("enrollment"));
        
        activityEventDao.deleteActivityEvents(healthCode);
    }
    
    private DynamoActivityEvent getEnrollmentEvent(DateTime timestamp) {
        return new DynamoActivityEvent.Builder().withHealthCode(healthCode)
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(timestamp).build();
    }
    
    private DynamoActivityEvent getSurveyFinishedEvent(DateTime timestamp) {
        return new DynamoActivityEvent.Builder().withHealthCode(healthCode)
                .withObjectType(ActivityEventObjectType.SURVEY).withEventType(ActivityEventType.FINISHED)
                .withTimestamp(timestamp).withObjectId("AAA-BBB-CCC").build();
    }
    
    private DynamoActivityEvent getQuestionAnsweredEvent(DateTime timestamp, String answer) {
        return new DynamoActivityEvent.Builder().withHealthCode(healthCode)
                .withObjectType(ActivityEventObjectType.QUESTION).withObjectId("DDD-EEE-FFF")
                .withEventType(ActivityEventType.ANSWERED).withAnswerValue(answer).withTimestamp(timestamp).build();
    }
    
    private DynamoActivityEvent getScheduledActivityFinishedEvent(DateTime timestamp) {
        return new DynamoActivityEvent.Builder().withHealthCode(healthCode).withObjectType(ActivityEventObjectType.ACTIVITY)
                .withObjectId("AAA-BBB-CCC").withEventType(ActivityEventType.FINISHED).withTimestamp(timestamp).build();
    }
}
