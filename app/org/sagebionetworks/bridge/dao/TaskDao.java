package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Task;

public interface TaskDao {
    
    /**
     * Get a user's tasks up to a target timestamp. This returns all tasks that are not expired, deleted 
     * or finished (technically defined as either having an expiration timestamp in the past, or having 
     * a finishedOn timestamp set).
     * 
     * @param studyIdentifier
     * @param user
     * @param endsOn
     * @return
     */
    public List<Task> getTasks(User user, DateTime endsOn);
    
    /**
     * Update the startedOn, finishedOn timestamps or the deleted flag of the tasks in the 
     * collection. Tasks in this collection should also have a GUID, all other fields are 
     * ignored.
     * 
     * @param healthCode
     * @param tasks
     */
    public void updateTasks(String healthCode, List<Task> tasks);
    
    /**
     * Not exposed in the API, this method does exactly the same things as getTasks(), but does not 
     * run the scheduler to add future tasks. Used to test after deletion. 
     * @return
     */
    public List<Task> getTasksWithoutScheduling(User user);
    
    /**
     * For internal deletion of a user, physical delete all the tasks associated with that user. This method should only
     * be called as a user is being deleted. To do a logical delete, change the deleted flag on a task and update the task.
     * 
     * @param healthCode
     */
    public void deleteTasks(String healthCode);
    
}
