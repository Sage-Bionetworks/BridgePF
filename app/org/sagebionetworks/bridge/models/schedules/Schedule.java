package org.sagebionetworks.bridge.models.schedules;

public class Schedule {
    
    public enum Type {
        DATE,
        CRON
    }

    public enum ActivityType {
        SURVEY,
        TASK
    }
    
    private String studyUserCompoundKey;
    private String guid;
    private String label;
    private ActivityType activityType;
    private String activityRef;
    private Schedule.Type scheduleType;
    private String schedule;
    private Long expires;
    
    public Schedule() {
        
    }
    
    public Schedule(Schedule schedule) {
        setStudyUserCompoundKey(schedule.getStudyUserCompoundKey());
        setGuid(schedule.getGuid());
        setLabel(schedule.getLabel());
        setActivityType(schedule.getActivityType());
        setActivityRef(schedule.getActivityRef());
        setScheduleType(schedule.getScheduleType());
        setSchedule(schedule.getSchedule());
        setExpires(schedule.getExpires());
    }
    
    public String getStudyUserCompoundKey() {
        return studyUserCompoundKey;
    }
    public void setStudyUserCompoundKey(String studyUserCompoundKey) {
        this.studyUserCompoundKey = studyUserCompoundKey;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public ActivityType getActivityType() {
        return activityType;
    }
    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }
    public String getActivityRef() {
        return activityRef;
    }
    public void setActivityRef(String activityRef) {
        this.activityRef = activityRef;
    }
    public Schedule.Type getScheduleType() {
        return scheduleType;
    }
    public void setScheduleType(Schedule.Type scheduleType) {
        this.scheduleType = scheduleType;
    }
    public String getSchedule() {
        return schedule;
    }
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    public Long getExpires() {
        return expires;
    }
    public void setExpires(Long expires) {
        this.expires = expires;
    }
    
}
