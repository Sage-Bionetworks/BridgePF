package org.sagebionetworks.bridge.models.schedules;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.ImmutableMap;

/**
 * All the information necessary to convert a schedule into a set of activities, on a given request. 
 * Because some of these values derive from the user, there is a validator that is run on this object 
 * that verifies the four required values (studyId, zone, endsOn and healthCode) are present.
 * 
 * @see org.sagebionetworks.bridge.validators.ScheduleContextValidator
 */
public final class ScheduleContext {
    
    private final StudyIdentifier studyId;
    private final String healthCode;
    private final ClientInfo clientInfo;
    private final DateTimeZone zone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final DateTime now;
    private final User user;
    private final Set<String> dataGroups;
    
    private ScheduleContext(ClientInfo clientInfo, DateTimeZone zone, DateTime endsOn,
            Map<String, DateTime> events, DateTime now, User user) {
        this.studyId = (user == null) ? null : new StudyIdentifierImpl(user.getStudyKey());
        this.healthCode = (user == null) ? null : user.getHealthCode();
        this.clientInfo = clientInfo;
        this.zone = zone;
        this.endsOn = endsOn;
        this.events = events;
        this.now = now;
        this.user = user;
        this.dataGroups = (user == null) ? null : user.getDataGroups();
    }
    
    /**
     * The study identifier for this participant.
     * @return
     */
    public StudyIdentifier getStudyIdentifier() {
        return studyId;
    }
    
    /**
     * Client information based on the supplied User-Agent header.
     * @return
     */
    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    /**
     * The time zone of the client at the time of this request. This allows the scheduler to accommodate 
     * moves between time zones.
     * @return
     */
    public DateTimeZone getZone() {
        return zone;
    }
    
    /**
     * The current request is asking for activities up to a given end date.
     * @return
     */
    public DateTime getEndsOn() {
        return endsOn;
    }

    /**
     * The current user's health code.
     * @return
     */
    public String getHealthCode() {
        return healthCode;
    }
    
    /**
     * Are there any events recorded for this participant? This should always return true since every 
     * participant should have an enrollment event, if nothing else.
     * @return
     */
    public boolean hasEvents() {
        return (events != null && !events.isEmpty());
    }
    
    /**
     * Get an event timestamp for a given event ID.
     * @param eventId
     * @return
     */
    public DateTime getEvent(String eventId) {
        return (events != null) ? events.get(eventId) : null;
    }
    
    /**
     * Returns now in the user's time zone. Practically this is not that important but 
     * it allows you to calculate all time calculations in one time zone, which is easier 
     * to reason about.
     * @return
     */
    public DateTime getNow() {
        return now;
    }
    
    public Set<String> getUserDataGroups() {
        return dataGroups;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, healthCode, clientInfo, zone, endsOn, events, now, user, dataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(studyId, other.studyId) && Objects.equals(healthCode, other.healthCode)
                && Objects.equals(endsOn, other.endsOn) && Objects.equals(zone, other.zone)
                && Objects.equals(clientInfo, other.clientInfo) && Objects.equals(events, other.events)
                && Objects.equals(now, other.now) && Objects.equals(user, other.user)
                && Objects.equals(dataGroups, other.dataGroups));
    }

    @Override
    public String toString() {
        return "ScheduleContext [clientInfo=" + clientInfo + ", zone=" + zone + ", endsOn=" + 
                endsOn + ", events=" + events + ", user=" + user + ", dataGroups=" + dataGroups + "]";
    }
    
    public static class Builder {
        private ClientInfo clientInfo;
        private DateTimeZone zone;
        private DateTime endsOn;
        private Map<String,DateTime> events;
        private DateTime now;
        private User user;
        
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        public Builder withTimeZone(DateTimeZone zone) {
            this.zone = zone;
            return this;
        }
        public Builder withEndsOn(DateTime endsOn) {
            this.endsOn = endsOn;
            return this;
        }
        public Builder withEvents(Map<String,DateTime> events) {
            if (events != null) {
                this.events = ImmutableMap.copyOf(events);    
            }
            return this;
        }
        public Builder withUser(User user) {
            this.user = user;
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            this.clientInfo = context.clientInfo;
            this.zone = context.zone;
            this.endsOn = context.endsOn;
            this.events = context.events;
            this.now = context.now;
            this.user = context.user;
            return this;
        }
        
        public ScheduleContext build() {
            if (now == null) {
                now = (zone == null) ? DateTime.now() : DateTime.now(zone);
            }
            ScheduleContext context = new ScheduleContext(clientInfo, zone, endsOn, events, now, user);
            // Not validating here. There are many tests to confirm that the scheduler will work with different 
            // time windows, but the validator ensures the context object is within the declared allowable
            // time window. This is validated in ScheduledActivityService.
            //Validate.nonEntityThrowingException(VALIDATOR, context);
            return context;
        }
    }
    
}
