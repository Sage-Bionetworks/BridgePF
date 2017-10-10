package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

public interface ScheduledActivityDao {
    
    /**
     * Get paged results of historical scheduled activities by an activity GUID.
     */
    ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistoryV2(String healthCode, String activityGuid,
            DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize);
    
    /**
     * Get paged results of historical scheduled activities by a GUID constructed from the task, compound
     * activity or survey that is referred to by the scheduled activity.
     */
    ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistoryV3(String healthCode, ActivityType activityType,
            String referentGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize);
    
    /**
     * Load an individual activity.
     */
    ScheduledActivity getActivity(DateTimeZone timeZone, String healthCode, String guid, boolean throwException);
   
    /**
     * Get a list of activities for a user. The list is derived from the scheduler.
     */
    List<ScheduledActivity> getActivities(DateTimeZone timeZone, List<ScheduledActivity> activities);
    
    /**
     * Save activities (activities will only be saved if they are not in the database).
     */
    void saveActivities(List<ScheduledActivity> activities);
    
    /**
     * Update the startedOn or finishedOn timestamps of the activities in the collection. Activities in this collection
     * should also have a GUID. All other fields are ignored. Health code is supplied here because these activities come from
     * the client and the client does not provide it.
     */
    void updateActivities(String healthCode, List<ScheduledActivity> activities);
    
    /**
     * Physically delete all the activity records for this user. This method should only be called as a 
     * user is being deleted. To do a logical delete, add a "finishedOn" timestamp to a scheduled activity 
     * and update it. 
     */
    void deleteActivitiesForUser(String healthCode);
    
}
