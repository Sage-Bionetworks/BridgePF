package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

public interface ScheduledActivityDao {
    
    /**
     * Get paged results of the scheduled activities that have been created for this user. 
     * @param healthCode
     * @param offsetKey
     * @param pageSize
     * @return
     */
    PagedResourceList<? extends ScheduledActivity> getActivityHistory(String healthCode, String offsetKey, int pageSize);
    
    /**
     * Load an individual activity.
     * @param timeZone
     * @param healthCode
     * @param guid
     * @return
     */
    public ScheduledActivity getActivity(DateTimeZone timeZone, String healthCode, String guid);
   
    /**
     * Get a list of activities for a user. The list is derived from the scheduler.
     * @param healthCode
     * @param timeZone
     * @param activityGuids
     * @return
     */
    public List<ScheduledActivity> getActivities(DateTimeZone timeZone, List<ScheduledActivity> activities);
    
    /**
     * Save activities (activities will only be saved if they are not in the database).
     * @param activities
     */
    public void saveActivities(List<ScheduledActivity> activities);
    
    /**
     * Update the startedOn or finishedOn timestamps of the activities in the collection. Activities in this collection
     * should also have a GUID. All other fields are ignored. Health code is supplied here because these activities come from
     * the client and the client does not provide it.
     * 
     * @param healthCode
     * @param activities
     */
    public void updateActivities(String healthCode, List<ScheduledActivity> activities);
    
    /**
     * Physically delete all the activity records for this user. This method should only be called as a 
     * user is being deleted. To do a logical delete, add a "finishedOn" timestamp to a scheduled activity 
     * and update it. 
     * 
     * @param healthCode
     */
    public void deleteActivitiesForUser(String healthCode);
    
}
