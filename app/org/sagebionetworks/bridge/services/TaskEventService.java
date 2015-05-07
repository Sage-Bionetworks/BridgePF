package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskEventService {

    private TaskEventDao taskEventDao;
    
    @Autowired
    public void setTaskEventDao(TaskEventDao taskEventDao) {
        this.taskEventDao = taskEventDao;
    }
    
    // Intend to create convenience methods for common event objects as we go.
    // That'll be the service's contribution.
    
    public void publishEvent(TaskEvent event) {
        checkNotNull(event);
        taskEventDao.publishEvent(event);
    }

    public Map<String, DateTime> getTaskEventMap(String healthCode) {
        checkNotNull(healthCode);
        return taskEventDao.getTaskEventMap(healthCode);
    }

    public void deleteTaskEvents(String healthCode) {
        checkNotNull(healthCode);
        taskEventDao.deleteTaskEvents(healthCode);
    }

}
