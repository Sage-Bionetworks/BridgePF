package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
import org.sagebionetworks.bridge.models.tasks.TaskEventObjectType;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;

@Component
public class TaskEventService {

    private TaskEventDao taskEventDao;
    
    @Autowired
    public void setTaskEventDao(TaskEventDao taskEventDao) {
        this.taskEventDao = taskEventDao;
    }
    
    public void publishEnrollmentEvent(String healthCode, UserConsent consent) {
        checkNotNull(consent);
        
        TaskEvent event = new DynamoTaskEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(consent.getSignedOn())
            .withObjectType(TaskEventObjectType.ENROLLMENT).build();
        taskEventDao.publishEvent(event);    
    }
    
    public void publishQuestionAnsweredEvent(String healthCode, SurveyAnswer answer) {
        checkNotNull(healthCode);
        checkNotNull(answer);
        
        TaskEvent event = new DynamoTaskEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(answer.getAnsweredOn())
            .withObjectType(TaskEventObjectType.QUESTION)
            .withObjectId(answer.getQuestionGuid())
            .withEventType(TaskEventType.ANSWERED)
            .withAnswerValue(Joiner.on(",").join(answer.getAnswers())).build();
        taskEventDao.publishEvent(event);
    }
    
    public void publishSurveyFinishedEvent(SurveyResponse response) {
        checkNotNull(response);
        
        TaskEvent event = new DynamoTaskEvent.Builder()
            .withHealthCode(response.getHealthCode())
            .withTimestamp(response.getCompletedOn())
            .withObjectType(TaskEventObjectType.SURVEY)
            .withObjectId(response.getSurveyGuid())
            .withEventType(TaskEventType.FINISHED)
            .build();
        taskEventDao.publishEvent(event);
    }
    
    /**
     * TaskEvents can be published directly, although all supported events have a more 
     * specific service method that should be preferred. This method can be used for 
     * edge cases (like answering a question or finishing a survey through the bulk import 
     * system, which does not generate SurveyResponse objects).
     * 
     * @param event
     */
    public void publishTaskEvent(TaskEvent event) {
        checkNotNull(event);
        taskEventDao.publishEvent(event);
    }

    /**
     * Gets the task events times for a specific user in order to schedule against them.
     * @param healthCode
     * @return
     */
    public Map<String, DateTime> getTaskEventMap(String healthCode) {
        checkNotNull(healthCode);
        return taskEventDao.getTaskEventMap(healthCode);
    }

    public void deleteTaskEvents(String healthCode) {
        checkNotNull(healthCode);
        taskEventDao.deleteTaskEvents(healthCode);
    }

}
