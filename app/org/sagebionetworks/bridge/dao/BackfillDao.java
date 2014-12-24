package org.sagebionetworks.bridge.dao;

import java.util.Iterator;
import java.util.List;

import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;

public interface BackfillDao {

    /**
     * Creates a new backfill task with the name of the task and the user who 
     * starts the backfill task.
     */
    BackfillTask createTask(String name, String user);

    /**
     * Updates the status of the backfill task.
     */
    void updateTaskStatus(String taskId, BackfillStatus status);

    /**
     * Gets the backfill task by ID.
     */
    BackfillTask getTask(String taskId);

    /**
     * Gets the list of tasks of the specified name
     * since a particular time point.
     */
    List<? extends BackfillTask> getTasks(String taskName, long since);

    /**
     * Creates a new backfill record for the specified task.
     */
    BackfillRecord createRecord(String taskId, String studyId, String accountId, String operation);

    /**
     * Gets the list of records of a particular task.
     */
    Iterator<? extends BackfillRecord> getRecords(String taskId);

    /**
     * Gets the number of records of a particular task.
     */
    int getRecordCount(String taskId);
}
