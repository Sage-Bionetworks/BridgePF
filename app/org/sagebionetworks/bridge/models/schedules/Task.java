package org.sagebionetworks.bridge.models.schedules;

import java.util.Comparator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoTask.class)
public interface Task {

    public static Task create() {
        return new DynamoTask();
    }

    // Sorts in reverse order.
    public static final Comparator<Task> TASK_COMPARATOR = new Comparator<Task>() {
        @Override
        public int compare(Task task1, Task task2) {
            // Sort tasks with no set scheduled time behind tasks with scheduled times.
            if (task1.getScheduledOn() == null) {
                return (task2.getScheduledOn() == null) ? 0 : 1;
            }
            if (task2.getScheduledOn() == null) {
                return -1;
            }
            int result = task1.getScheduledOn().compareTo(task2.getScheduledOn());
            if (result == 0) {
                Activity act1 = task1.getActivity();
                Activity act2 = task2.getActivity();
                if (act1 != null && act1.getLabel() != null && act2 != null && act2.getLabel() != null) {
                    result = task1.getActivity().getLabel().compareTo(task2.getActivity().getLabel());
                }
            }
            return result;
        }
    };

    public TaskStatus getStatus();

    /**
     * Get the time zone for this request. Currently this is a field on the task and must be set to get DateTime values
     * from other fields in the class. This forces one method of converting schedule times to local times in order to
     * satisfy the API's delivery of times in the user's time zone, and may change when we convert closer to the service
     * layer and remove this as a consideration from task construction.
     * 
     * @return
     */
    public DateTimeZone getTimeZone();

    public void setTimeZone(DateTimeZone timeZone);

    public String getGuid();

    public void setGuid(String guid);

    public String getHealthCode();

    public void setHealthCode(String healthCode);

    public Activity getActivity();

    public void setActivity(Activity activity);

    public DateTime getScheduledOn();

    public void setScheduledOn(DateTime scheduledOn);

    public DateTime getExpiresOn();

    public void setExpiresOn(DateTime expiresOn);

    public Long getStartedOn();

    public void setStartedOn(Long startedOn);

    public Long getFinishedOn();

    public void setFinishedOn(Long finishedOn);

    public Long getHidesOn();

    public void setHidesOn(Long hidesOn);

    public String getRunKey();

    public void setRunKey(String runKey);

    public boolean getPersistent();

    public void setPersistent(boolean persistent);
    
    public void setMinAppVersion(Integer minAppVersion);
    
    public Integer getMinAppVersion();
    
    public void setMaxAppVersion(Integer maxAppVersion);
    
    public Integer getMaxAppVersion();
    

}
