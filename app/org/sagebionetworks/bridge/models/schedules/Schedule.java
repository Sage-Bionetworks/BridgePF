package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSchedule.class)
@BridgeTypeName("Schedule")
public interface Schedule extends BridgeEntity {
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getStudyUserCompoundKey();
    public void setStudyUserCompoundKey(String studyUserCompoundKey);
    public void setStudyAndUser(Study study, User user);
    
    public void setSchedulePlanGuid(String schedulePlanGuid);
    public String getSchedulePlanGuid();
    
    public String getLabel();
    public void setLabel(String label);
    
    public ActivityType getActivityType();
    public void setActivityType(ActivityType activityType);
    
    public String getActivityRef();
    public void setActivityRef(String activityRef);
    
    public ScheduleType getScheduleType();
    public void setScheduleType(ScheduleType scheduleType);
    
    public String getSchedule();
    public void setSchedule(String schedule);
    
    public Long getExpires();
    public void setExpires(Long expires);

    public Schedule copy();
}
