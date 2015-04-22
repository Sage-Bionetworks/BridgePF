package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.Period;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface TaskDao {
    
    /**
     * Get a user's tasks up to an indicated date and time. The time window will be the period before "now" 
     * to the period after "now", expressed as time durations. So for example, before of P2D and after of P2D 
     * will return tasks over four days, two days before and two days after the request.
     * 
     * @param studyIdentifier
     * @param user
     * @param startsOn
     * @param endsOn
     * @return
     */
    public List<Task> getTasks(User user, Period startsOn, Period endsOn);
    
    /**
     * Update the startedOn or completedOn timestamps of the tasks indicated in the collection. Tasks in this collection
     * should also have a GUID, but don't require any other fields.
     * 
     * @param healthCode
     * @param tasks
     */
    public void updateTasks(String healthCode, List<Task> tasks);
    
    /**
     * For internal deletion of a user, delete all the tasks associated with that user.
     * @param healthCode
     */
    public void deleteTasks(String healthCode);
    
}
