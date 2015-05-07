package org.sagebionetworks.bridge.models.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent.Builder;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

import com.google.common.collect.Lists;

public class TaskEventTest {

    @Test
    public void cannotConstructBadTaskEvent() {
        try {
            new DynamoTaskEvent.Builder().build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("type cannot be null", e.getErrors().get("type").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withHealthCode("BBB").build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("type cannot be null", e.getErrors().get("type").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withType(TaskEventType.QUESTION).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("timestamp cannot be null", e.getErrors().get("timestamp").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("type cannot be null", e.getErrors().get("type").get(0));
            assertEquals("healthCode cannot be null or blank", e.getErrors().get("healthCode").get(0));
        }
        try {
            new DynamoTaskEvent.Builder().withHealthCode("BBB").withType(TaskEventType.ENROLLMENT)
                            .withTimestamp(DateTime.now()).withId("AAA-BBB-CCC").build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("action cannot be null if ID is present", e.getErrors().get("action").get(0));
        }
    }
    
    @Test
    public void canConstructSimpleEventId() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoTaskEvent.Builder();
        TaskEvent event = builder.withType(TaskEventType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, event.getTimestamp());
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void canConstructEventNoAction() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoTaskEvent.Builder();
        TaskEvent event = builder.withType(TaskEventType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals("BBB", event.getHealthCode());
        assertEquals(now, event.getTimestamp());
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void simpleTaskEventIdIsCorrect() {
        TaskEvent event = new DynamoTaskEvent.Builder().withHealthCode("BBB").withType(TaskEventType.ENROLLMENT)
                        .withTimestamp(DateTime.now()).build();
        
        assertEquals("enrollment", event.getEventId());
    }
    
    @Test
    public void fullTaskEventIdWithValueIsCorrect() {
        DateTime now = DateTime.now();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnsweredOn(now.getMillis());
        answer.setQuestionGuid("BBB-CCC-DDD");
        answer.setAnswers(Lists.newArrayList("belgium"));
        
        TaskEvent event = new DynamoTaskEvent.Builder().withHealthCode("BBB").answeringSurvey(answer).build();
        
        assertEquals("question:BBB-CCC-DDD:answered=belgium", event.getEventId());
    }

    @Test
    public void taskEventForFinishedSurvey() {
        DateTime now = DateTime.now();
        
        Survey survey = new DynamoSurvey();
        survey.setGuid("BBB-CCC-DDD");
        
        TaskEvent event = new DynamoTaskEvent.Builder().withHealthCode("BBB").withTimestamp(now).finishingSurvey(survey)
                        .withAction(TaskEventAction.FINISHED).build();
        
        assertEquals("survey:BBB-CCC-DDD:finished", event.getEventId());
    }
    
}
