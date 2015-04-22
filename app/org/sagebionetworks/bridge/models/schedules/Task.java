package org.sagebionetworks.bridge.models.schedules;

public interface Task {
    
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
