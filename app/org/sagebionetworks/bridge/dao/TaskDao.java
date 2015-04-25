package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Task;

public interface TaskDao {
    
    /**
     * Get a user's tasks up to a target timestamp. This returns all tasks that are not expired, deleted 
     * or finished, as well as future tasks that are scheduled but should not be started yet.
     * 
     * @param user
     * @param endsOn
     * @return
     */
    public List<Task> getTasks(User user, DateTime endsOn);
    
    /**
     * Update the startedOn or finishedOn timestamps of the tasks in the collection. Tasks in this 
     * collection should also have a GUID. All other fields are ignored.
     * 
     * @param healthCode
     * @param tasks
     */
    public void updateTasks(String healthCode, List<Task> tasks);
    
    /**
     * Not exposed in the API, this method does exactly the same things as getTasks(), but does not 
     * run the scheduler to add future tasks. Used to test after deletion. 
     * 
     * @param user
     * @return
     */
    public List<Task> getTasksWithoutScheduling(User user);
    
    /**
     * Physically delete all the task records for this user. This method should only be called as a 
     * user is being deleted. To do a logical delete, add a "finishedOn" timestamp to a task and 
     * update it. 
     * 
     * @param healthCode
     */
    public void deleteTasks(String healthCode);
    
}
