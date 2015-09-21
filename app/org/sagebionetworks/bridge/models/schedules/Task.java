package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoTask.class)
public interface Task {
    
    public TaskStatus getStatus();
    
    public String getGuid();
    public void setGuid(String guid);
    
    public Activity getActivity();
    public void setActivity(Activity activity);

    public DateTime getScheduledOn();
    public DateTime getExpiresOn();
    
    public Long getStartedOn();
    public void setStartedOn(Long startedOn);
    
    public Long getFinishedOn();
    public void setFinishedOn(Long finishedOn);
    
    public Long getHidesOn();
    public void setHidesOn(Long hidesOn);
    
    public String getRunKey();
    public void setRunKey(String runKey);
    
    public boolean getPersistent();
    public void setPersistent(boolean persistent);

}
