package org.sagebionetworks.bridge.models.schedules;

import java.util.Comparator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(as = DynamoScheduledActivity.class)
public interface ScheduledActivity extends BridgeEntity {
    
    /**
     * Due to the use of the DynamoIndexHelper, which uses JSON deserialization to recover object
     * structure, we do not use @JsonIgnore annotation on DynamoScheduledActivity. Instead, we 
     * exclude those values using a filter and this writer.
     */
    ObjectWriter SCHEDULED_ACTIVITY_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter", 
        SimpleBeanPropertyFilter.serializeAllExcept("healthCode", "schedulePlanGuid")));

    /**
     * Researchers get the schedule plan GUID. 
     */
    ObjectWriter RESEARCHER_SCHEDULED_ACTIVITY_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter", 
            SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));
    
    static ScheduledActivity create() {
        return new DynamoScheduledActivity();
    }

    // Sorts in temporal order, oldest to newest activity, labels alphabetically if at the same moment in time.
    Comparator<ScheduledActivity> SCHEDULED_ACTIVITY_COMPARATOR = new Comparator<ScheduledActivity>() {
        @Override
        public int compare(ScheduledActivity scheduledActivity1, ScheduledActivity scheduledActivity2) {
            // Sort activities with no set scheduled time behind activities with scheduled times.
            if (scheduledActivity1.getScheduledOn() == null) {
                return (scheduledActivity2.getScheduledOn() == null) ? 0 : 1;
            }
            if (scheduledActivity2.getScheduledOn() == null) {
                return -1;
            }
            int result = scheduledActivity1.getScheduledOn().compareTo(scheduledActivity2.getScheduledOn());
            if (result == 0) {
                Activity act1 = scheduledActivity1.getActivity();
                Activity act2 = scheduledActivity2.getActivity();
                if (act1 != null && act1.getLabel() != null && act2 != null && act2.getLabel() != null) {
                    result = scheduledActivity1.getActivity().getLabel().compareTo(scheduledActivity2.getActivity().getLabel());
                }
            }
            return result;
        }
    };

    ScheduledActivityStatus getStatus();

    /**
     * BRIDGE-1589. Carry over the schedule used to generate a ScheduledActivity in order to infer one-time tasks 
     * that may have been duplicated as a result of scheduling from enrollment in a particular time zone. This 
     * schedule is not persisted or returned to the user.
     */
    Schedule getSchedule();
    
    void setSchedule(Schedule schedule);
    
    /**
     * Get the time zone for this request. Currently this is a field on the activity and must be set to get DateTime values
     * from other fields in the class. This forces one method of converting schedule times to local times in order to
     * satisfy the API's delivery of times in the user's time zone, and may change when we convert closer to the service
     * layer and remove this as a consideration from activity construction.
     */
    DateTimeZone getTimeZone();

    void setTimeZone(DateTimeZone timeZone);
    
    String getSchedulePlanGuid();

    void setSchedulePlanGuid(String schedulePlanGuid);

    String getGuid();

    void setGuid(String guid);

    String getHealthCode();

    void setHealthCode(String healthCode);

    Activity getActivity();

    void setActivity(Activity activity);

    DateTime getScheduledOn();

    void setScheduledOn(DateTime scheduledOn);

    DateTime getExpiresOn();

    void setExpiresOn(DateTime expiresOn);

    Long getStartedOn();

    void setStartedOn(Long startedOn);

    Long getFinishedOn();

    void setFinishedOn(Long finishedOn);

    boolean getPersistent();

    void setPersistent(boolean persistent);
    
}
