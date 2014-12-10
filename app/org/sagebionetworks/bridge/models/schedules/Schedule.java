package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.Study;

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
    
    public List<Activity> getActivities();
    public void setActivities(List<Activity> activities);
    
    public ActivityType getActivityType();
    // public void setActivityType(ActivityType activityType);
    
    public String getActivityRef();
    // public void setActivityRef(String activityRef);
    
    public ScheduleType getScheduleType();
    public void setScheduleType(ScheduleType scheduleType);
    
    public boolean isScheduleFor(GuidCreatedOnVersionHolder keys);
    
    public Long getStartsOn();
    public void setStartsOn(Long startsOn);
    
    public Long getEndsOn();
    public void setEndsOn(Long endsOn);
    
    public String getCronTrigger();
    public void setCronTrigger(String cronTrigger);
    
    public Long getExpires();
    public void setExpires(Long expires);

    public Schedule copy();
}
