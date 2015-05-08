package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;

public interface TaskEventDao {

    public void publishEvent(TaskEvent event);
    
    public Map<String, DateTime> getTaskEventMap(String healthCode);
    
    public void deleteTaskEvents(String healthCode);
}
