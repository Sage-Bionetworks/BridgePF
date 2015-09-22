package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;

public interface TaskDao {
    
    /**
     * Get a user's tasks up to a target timestamp. This returns all tasks that are not expired, deleted 
     * or finished, as well as future tasks that are scheduled but should not be started yet. Tasks are 
     * not necessarily saved in the database, however.
     * 
     * @param context
     * @return
     */
    public List<Task> getTasks(ScheduleContext context);
    
    /**
     * Have any of the tasks for this run key been created?
     * @param healthCode
     * @param runKey
     * @return
     */
    public boolean taskRunHasNotOccurred(String healthCode, String runKey);
    
    /**
     * Save tasks (tasks will only be saved if they are not in the database).
     * @param healthCode
     * @param tasks
     */
    public void saveTasks(String healthCode, List<Task> tasks);
    
    /**
     * Update the startedOn or finishedOn timestamps of the tasks in the collection. Tasks in this 
     * collection should also have a GUID. All other fields are ignored. 
     * 
     * @param healthCode
     * @param tasks
     */
    public void updateTasks(String healthCode, List<Task> tasks);
    
    /**
     * Physically delete all the task records for this user. This method should only be called as a 
     * user is being deleted. To do a logical delete, add a "finishedOn" timestamp to a task and 
     * update it. 
     * 
     * @param healthCode
     */
    public void deleteTasks(String healthCode);
    
}
