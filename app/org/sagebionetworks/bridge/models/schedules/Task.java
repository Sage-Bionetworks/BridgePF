package org.sagebionetworks.bridge.models.schedules;

public interface Task {

    /**
     * This is a key that uniquely defines a task based on the data in the task 
     * (rather than the GUID, which is a synthetic key). A task is unique when 
     * you include the schedule plan GUID, the scheduledOn time, and the activity ref.
     */
    public String getNaturalKey();
    
    public TaskStatus getStatus();
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getSchedulePlanGuid();
    public void setSchedulePlanGuid(String schedulePlanGuid);
    
    public Activity getActivity();
    public void setActivity(Activity activity);
    
    public Long getScheduledOn();
    public void setScheduledOn(Long scheduledOn);
    
    public Long getExpiresOn();
    public void setExpiresOn(Long expiresOn);
    
    public Long getStartedOn();
    public void setStartedOn(Long startedOn);
    
    public Long getFinishedOn();
    public void setFinishedOn(Long finishedOn);

}
