package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoActivityEventDaoTest {

    @Resource
    ActivityEventDao activityEventDao;
    
    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoActivityEvent.class);
    }
    
    @Test
    public void canCrudEvent() {
        DateTime time1 = DateTime.now();
        DateTime time2 = time1.plusDays(1);
        DateTime time3 = time1.plusDays(2);
        DateTime time4 = time1.plusDays(3);
        DateTime time5 = time1.plusDays(4);
        
        // This is an answer event. It's key should be "question:CCC:answered" with a value column
        // the activity event map should create a key with the value, such as "question:CCC:answered=value"
        ActivityEvent event = getEnrollmentEvent(time1);
        activityEventDao.publishEvent(event);
        event = getSurveyFinishedEvent(time2);
        activityEventDao.publishEvent(event);
        event = getQuestionAnsweredEvent(time3, "someValue");
        activityEventDao.publishEvent(event);
        
        Map<String,DateTime> map = activityEventDao.getActivityEventMap("BBB");
        assertEquals(3, map.size());
        assertEquals(time1, map.get("enrollment"));
        assertEquals(time2, map.get("survey:AAA-BBB-CCC:finished"));
        assertEquals(time3, map.get("question:DDD-EEE-FFF:answered=someValue"));
        
        // Update timestamp of answer event while keeping same answer
        event = getQuestionAnsweredEvent(time4, "someValue");
        activityEventDao.publishEvent(event);
        
        map = activityEventDao.getActivityEventMap("BBB");
        assertEquals(time4, map.get("question:DDD-EEE-FFF:answered=someValue"));
        
        // Update answer event with different answer and later timestamp
        event = getQuestionAnsweredEvent(time5, "anotherAnswer");
        activityEventDao.publishEvent(event);
        
        // Creates a different key in activity event map. Researchers schedule against specific answers.
        map = activityEventDao.getActivityEventMap("BBB");
        assertEquals(time5, map.get("question:DDD-EEE-FFF:answered=anotherAnswer"));
        // The key point here is that the other answer is no longer in the map, so there can't be 
        // an "either or" scheduling conflict. The user can only answer one way or another on a 
        // given question, even if the answer is updated.
        assertNull(map.get("question:DDD-EEE-FFF:answered=someAnswer"));
        
        activityEventDao.deleteActivityEvents("BBB");
        
        map = activityEventDao.getActivityEventMap("BBB");
        assertEquals(0, map.size());
    }
    
    @Test
    public void neverUpdateEnrollmentTaskEvent() {
        final DateTime firstEvent = DateTime.now();
        
        ActivityEvent event = getEnrollmentEvent(firstEvent);
        activityEventDao.publishEvent(event);
        
        // This does not work. You can't do this.
        event = getEnrollmentEvent(firstEvent.plusHours(2));
        activityEventDao.publishEvent(event);
        
        Map<String,DateTime> eventMap = activityEventDao.getActivityEventMap("BBB");
        assertEquals(firstEvent, eventMap.get("enrollment"));
    }
    
    private DynamoActivityEvent getEnrollmentEvent(DateTime timestamp) {
        return new DynamoActivityEvent.Builder().withHealthCode("BBB")
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(timestamp).build();
    }
    
    private DynamoActivityEvent getSurveyFinishedEvent(DateTime timestamp) {
        return new DynamoActivityEvent.Builder().withHealthCode("BBB").withObjectType(ActivityEventObjectType.SURVEY)
            .withEventType(ActivityEventType.FINISHED).withTimestamp(timestamp).withObjectId("AAA-BBB-CCC").build();
    }
    
    private DynamoActivityEvent getQuestionAnsweredEvent(DateTime timestamp, String answer) {
        return new DynamoActivityEvent.Builder().withHealthCode("BBB")
            .withObjectType(ActivityEventObjectType.QUESTION).withObjectId("DDD-EEE-FFF")
            .withEventType(ActivityEventType.ANSWERED).withAnswerValue(answer).withTimestamp(timestamp).build();
    }
}
