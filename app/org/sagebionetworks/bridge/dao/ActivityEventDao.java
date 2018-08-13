package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;

public interface ActivityEventDao {

    /**
     * Publish an event into this user's event stream. This event becomes available 
     * for scheduling activities for this user. If the enforceLater flag is true, then 
     * only non-enrollment updates that occur after any existing event will be persisted. 
     * If the flag is false, this event will always be persisted (useful only for testing 
     * purposes).
     */
    void publishEvent(ActivityEvent event, boolean enforceLater);
    
    /**
     * Get a map of events, where the string key is an event identifier, and the value 
     * is the timestamp of the event. This map will include calculated events like 
     * "two_weeks_before_enrollment".
     * 
     * @see org.sagebionetworks.bridge.models.activities.ActivityEventObjectType
     */
    Map<String, DateTime> getActivityEventMap(String healthCode);
    
    /**
     * Delete all activity events for this user. This should only be called when physically 
     * deleting test users; users in production take too many server resources to completely 
     * delete this way.
     */
    void deleteActivityEvents(String healthCode);
    
    /**
     * Delete a single activity event for this user. 
     */
    void deleteActivityEvent(String healthCode, String eventId);
}
