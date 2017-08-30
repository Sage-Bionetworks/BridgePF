package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_JOINER;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

@Component
public class ActivityEventService {

    private ActivityEventDao activityEventDao;
    private StudyService studyService;

    @Autowired
    final void setActivityEventDao(ActivityEventDao activityEventDao) {
        this.activityEventDao = activityEventDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    public void publishCustomEvent(StudyIdentifier studyId, String healthCode, String eventKey, DateTime timestamp) {
        checkNotNull(healthCode);
        checkNotNull(eventKey);

        Study study = studyService.getStudy(studyId);

        if (!study.getActivityEventKeys().contains(eventKey)) {
            throw new BadRequestException("Study's ActivityEventKeys does not contain eventKey: " + eventKey);
        }

        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode(healthCode)
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .withObjectId(eventKey)
                .withTimestamp(timestamp).build();
        activityEventDao.publishEvent(event);
    }

    public void publishEnrollmentEvent(String healthCode, ConsentSignature signature) {
        checkNotNull(signature);
        
        ActivityEvent event = new DynamoActivityEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(signature.getSignedOn())
            .withObjectType(ActivityEventObjectType.ENROLLMENT).build();
        activityEventDao.publishEvent(event);    
    }
    
    public void publishQuestionAnsweredEvent(String healthCode, SurveyAnswer answer) {
        checkNotNull(healthCode);
        checkNotNull(answer);
        
        ActivityEvent event = new DynamoActivityEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(answer.getAnsweredOn())
            .withObjectType(ActivityEventObjectType.QUESTION)
            .withObjectId(answer.getQuestionGuid())
            .withEventType(ActivityEventType.ANSWERED)
            .withAnswerValue(COMMA_JOINER.join(answer.getAnswers())).build();
        activityEventDao.publishEvent(event);
    }
    
    public void publishActivityFinishedEvent(ScheduledActivity schActivity) {
        checkNotNull(schActivity);
        
        // If there's no colon, this is an existing activity and it cannot fire an 
        // activity event. Quietly ignore this until we have migrated activities.
        if (schActivity.getGuid().contains(":")) {
            String activityGuid = schActivity.getGuid().split(":")[0];
            
            ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode(schActivity.getHealthCode())
                .withObjectType(ActivityEventObjectType.ACTIVITY)
                .withObjectId(activityGuid)
                .withEventType(ActivityEventType.FINISHED)
                .withTimestamp(schActivity.getFinishedOn())
                .build();

            activityEventDao.publishEvent(event);
        }
    }
    
    /**
     * ActivityEvents can be published directly, although all supported events have a more 
     * specific service method that should be preferred. This method can be used for 
     * edge cases (like answering a question or finishing a survey through the bulk import 
     * system).
     * 
     * @param event
     */
    public void publishActivityEvent(ActivityEvent event) {
        checkNotNull(event);
        activityEventDao.publishEvent(event);
    }

    /**
     * Gets the activity events times for a specific user in order to schedule against them.
     * @param healthCode
     * @return
     */
    public Map<String, DateTime> getActivityEventMap(String healthCode) {
        checkNotNull(healthCode);
        return activityEventDao.getActivityEventMap(healthCode);
    }

    public List<ActivityEvent> getActivityEventList(String healthCode) {
        Map<String, DateTime> activityEvents = getActivityEventMap(healthCode);

        List<ActivityEvent> activityEventList = Lists.newArrayList();
        for (Map.Entry<String, DateTime> entry : activityEvents.entrySet()) {
            DynamoActivityEvent event = new DynamoActivityEvent();
            event.setEventId(entry.getKey());

            DateTime timestamp = entry.getValue();
            if (timestamp !=null) {
                event.setTimestamp(timestamp.getMillis());
            }

            activityEventList.add(event);
        }
        return activityEventList;
    }

    public void deleteActivityEvents(String healthCode) {
        checkNotNull(healthCode);
        activityEventDao.deleteActivityEvents(healthCode);
    }

}
