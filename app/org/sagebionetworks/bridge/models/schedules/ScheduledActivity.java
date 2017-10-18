package org.sagebionetworks.bridge.models.schedules;

import java.util.Comparator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;

import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.databind.JsonNode;
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
        SimpleBeanPropertyFilter.serializeAllExcept("healthCode", "referentGuid")));

    /**
     * Researchers get the schedule plan GUID. 
     */
    ObjectWriter RESEARCHER_SCHEDULED_ACTIVITY_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter", 
            SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));
    
    static ScheduledActivity create() {
        return new DynamoScheduledActivity();
    }

    public static int compareByReferentGuidThenGuid(ScheduledActivity activity1, ScheduledActivity activity2) {
        int result = activity1.getReferentGuid().compareTo(activity2.getReferentGuid());
        if (result == 0) {
            return activity1.getGuid().compareTo(activity2.getGuid());
        }
        return result;
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
    
    @DynamoDBIgnore
    default ScheduledActivityStatus getStatus() {
        if (getFinishedOn() != null && getStartedOn() == null) {
            return ScheduledActivityStatus.DELETED;
        } else if (getFinishedOn() != null && getStartedOn() != null) {
            return ScheduledActivityStatus.FINISHED;
        } else if (getStartedOn() != null) {
            return ScheduledActivityStatus.STARTED;
        }
        // We freeze the user's time zone to the initial time zone in order to prevent duplicates.
        // For schedules that use the time portion of an event timestamp, this can cause odd 
        // behavior when a user changes time zones. For the most part this resolves and is  
        // acceptable, but persistent tasks are logically always available, even if you move in 
        // a way that would shift the task to "scheduled" for some number of hours. Just prevent
        // this.
        if (getPersistent()) {
            return ScheduledActivityStatus.AVAILABLE;
        }
        if (getTimeZone() != null) {
            DateTime now = DateTime.now(getTimeZone());
            DateTime expiresOn = getExpiresOn();
            DateTime scheduledOn = getScheduledOn();
            if (expiresOn != null && now.isAfter(expiresOn)) {
                return ScheduledActivityStatus.EXPIRED;
            } else if (scheduledOn != null && now.isBefore(scheduledOn)) {
                return ScheduledActivityStatus.SCHEDULED;
            }
        }
        return ScheduledActivityStatus.AVAILABLE;
    }

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

    void setLocalScheduledOn(LocalDateTime localScheduledOn);
    
    JsonNode getClientData();
    
    void setClientData(JsonNode clientData);
    
    DateTime getExpiresOn();

    void setLocalExpiresOn(LocalDateTime expiresOn);

    Long getStartedOn();

    void setStartedOn(Long startedOn);

    Long getFinishedOn();

    void setFinishedOn(Long finishedOn);

    boolean getPersistent();

    void setPersistent(boolean persistent);
    
    void setReferentGuid(String guid);
    
    String getReferentGuid();
    
}
