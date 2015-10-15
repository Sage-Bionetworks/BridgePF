package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;

public interface ActivityEventDao {

    public void publishEvent(ActivityEvent event);
    
    public Map<String, DateTime> getActivityEventMap(String healthCode);
    
    public void deleteActivityEvents(String healthCode);
}
