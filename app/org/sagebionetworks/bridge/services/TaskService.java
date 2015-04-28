package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskService {
    
    public static final int DEFAULT_EXPIRES_ON_DAYS = 2;
    public static final int MAX_EXPIRES_ON_DAYS = 4;
    
    private TaskDao taskDao;
    
    @Autowired
    public void setTaskDao(TaskDao taskDao) {
        this.taskDao = taskDao;
    }
    
    public List<Task> getTasks(User user, DateTime endsOn) {
        checkNotNull(user);
        checkNotNull(endsOn);
        
        DateTime now = DateTime.now();
        if (endsOn.isBefore(now)) {
            throw new BadRequestException("End timestamp must be after the time of the request");
        } else if (endsOn.minusDays(MAX_EXPIRES_ON_DAYS).isAfter(now)) {
            throw new BadRequestException("Task request window must be "+MAX_EXPIRES_ON_DAYS+" days or less");
        }
        return taskDao.getTasks(user, endsOn);
    }
    
    public void updateTasks(String healthCode, List<Task> tasks) {
        checkArgument(isNotBlank(healthCode));
        checkNotNull(tasks);
        for (int i=0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task == null) {
                throw new BadRequestException("A task in the array is null");
            }
            if (task.getGuid() == null) {
                throw new BadRequestException(String.format("Task #%s has no GUID", i));
            }
        }
        
        taskDao.updateTasks(healthCode, tasks);
    }
    
    public void deleteTasks(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        taskDao.deleteTasks(healthCode);
    };
    
}
