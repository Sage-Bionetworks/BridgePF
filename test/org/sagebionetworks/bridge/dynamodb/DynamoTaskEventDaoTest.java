package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
import org.sagebionetworks.bridge.models.tasks.TaskEventObjectType;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoTaskEventDaoTest {

    @Resource
    TaskEventDao taskEventDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoTaskEvent.class);
        DynamoTestUtil.clearTable(DynamoTaskEvent.class);
    }
    
    @Test
    public void canCrudEvent() {
        DateTime time1 = DateTime.now();
        DateTime time2 = time1.plusDays(1);
        DateTime time3 = time1.plusDays(2);
        DateTime time4 = time1.plusDays(3);
        DateTime time5 = time1.plusDays(4);
        
        // This is an answer event. It's key should be "question:CCC:answered" with a value column
        // the task event map should create a key with the value, such as "question:CCC:answered=value"
        TaskEvent event = getEnrollmentEvent(time1);
        taskEventDao.publishEvent(event);
        event = getSurveyFinishedEvent(time2);
        taskEventDao.publishEvent(event);
        event = getQuestionAnsweredEvent(time3, "someValue");
        taskEventDao.publishEvent(event);
        
        Map<String,DateTime> map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(3, map.size());
        assertEquals(time1, map.get("enrollment"));
        assertEquals(time2, map.get("survey:AAA-BBB-CCC:finished"));
        assertEquals(time3, map.get("question:DDD-EEE-FFF:answered=someValue"));
        
        // Update timestamp of answer event while keeping same answer
        event = getQuestionAnsweredEvent(time4, "someValue");
        taskEventDao.publishEvent(event);
        
        map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(time4, map.get("question:DDD-EEE-FFF:answered=someValue"));
        
        // Update answer event with different answer and later timestamp
        event = getQuestionAnsweredEvent(time5, "anotherAnswer");
        taskEventDao.publishEvent(event);
        
        // Creates a different key in task event map. Researchers schedule against specific answers.
        map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(time5, map.get("question:DDD-EEE-FFF:answered=anotherAnswer"));
        // The key point here is that the other answer is no longer in the map, so there can't be 
        // an "either or" scheduling conflict. The user can only answer one way or another on a 
        // given question, even if the answer is updated.
        assertNull(map.get("question:DDD-EEE-FFF:answered=someAnswer"));
        
        taskEventDao.deleteTaskEvents("BBB");
        
        map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(0, map.size());
    }
    
    private DynamoTaskEvent getEnrollmentEvent(DateTime timestamp) {
        return new DynamoTaskEvent.Builder().withHealthCode("BBB")
            .withObjectType(TaskEventObjectType.ENROLLMENT).withTimestamp(timestamp).build();
    }
    
    private DynamoTaskEvent getSurveyFinishedEvent(DateTime timestamp) {
        return new DynamoTaskEvent.Builder().withHealthCode("BBB").withObjectType(TaskEventObjectType.SURVEY)
            .withEventType(TaskEventType.FINISHED).withTimestamp(timestamp).withObjectId("AAA-BBB-CCC").build();
    }
    
    private DynamoTaskEvent getQuestionAnsweredEvent(DateTime timestamp, String answer) {
        return new DynamoTaskEvent.Builder().withHealthCode("BBB")
            .withObjectType(TaskEventObjectType.QUESTION).withObjectId("DDD-EEE-FFF")
            .withEventType(TaskEventType.ANSWERED).withAnswerValue(answer).withTimestamp(timestamp).build();
    }
}
