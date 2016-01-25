package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

public interface ScheduledActivityDao {

    /**
     * Load an individual activity.
     * 
     * @param timeZone
     * @param healthCode
     * @param guid
     * @return
     */
    public ScheduledActivity getActivity(DateTimeZone timeZone, String healthCode, String guid);

    /**
     * Get a list of activities for a user. The list is derived from the scheduler.
     * 
     * @param healthCode
     * @param timeZone
     * @param activityGuids
     * @return
     */
    public List<ScheduledActivity> getActivities(DateTimeZone timeZone, List<ScheduledActivity> activities);

    /**
     * Save activities (activities will only be saved if they are not in the database).
     * 
     * @param activities
     */
    public void saveActivities(List<ScheduledActivity> activities);

    /**
     * Update the startedOn or finishedOn timestamps of the activities in the collection. Activities in this collection
     * should also have a GUID. All other fields are ignored. Health code is supplied here because these activities come
     * from the client and the client does not provide it.
     * 
     * @param healthCode
     * @param activities
     */
    public void updateActivities(String healthCode, List<ScheduledActivity> activities);

    /**
     * Physically delete all the activity records for this user. This method should only be called as a user is being
     * deleted. To do a logical delete, add a "finishedOn" timestamp to a scheduled activity and update it.
     * 
     * @param healthCode
     */
    public void deleteActivitiesForUser(String healthCode);

    /**
     * Delete the scheduled activities for a schedule plan, with the exception of started activities. While this is very
     * helpful for study development, when schedule plans and activities are changing and being tested, in production
     * the activities for a plan can number in the tens or hundreds of thousands, so we <em>only</em> do this when the
     * total number of activities are under a specified threshold count.
     * 
     * @param schedulePlanGuid
     */
    public void deleteActivitiesForSchedulePlanIfUnderThreshold(String schedulePlanGuid, int threshold);

}
