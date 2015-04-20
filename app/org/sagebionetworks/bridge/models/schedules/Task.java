package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.DateTime;

public interface Task {

    public String getGuid();
    public void setGuid(String guid);
    
    public String getSchedulePlanGuid();
    public void setSchedulePlanGuid(String schedulePlanGuid);
    
    public Activity getActivity();
    public void setActivity(Activity activity);
    
    public DateTime getScheduledOn();
    public void setScheduledOn(DateTime scheduledOn);
    
    public DateTime getExpiresOn();
    public void setExpiresOn(DateTime expiresOn);
    
}
