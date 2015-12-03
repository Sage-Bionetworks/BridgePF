package org.sagebionetworks.bridge.models.schedules;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * All the information necessary to convert a schedule into a set of activities, on a given request. 
 * Because some of these values derive from the user, there is a validator that is run on this object 
 * that verifies the four required values (studyId, zone, endsOn and healthCode) are present.
 * 
 * @see org.sagebionetworks.bridge.validators.ScheduleContextValidator
 */
public final class ScheduleContext {
    
    private final StudyIdentifier studyId;
    private final ClientInfo clientInfo;
    private final DateTimeZone zone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final String healthCode;
    private final DateTime now;
    private final Set<String> userDataGroups;
    
    private ScheduleContext(StudyIdentifier studyId, ClientInfo clientInfo, DateTimeZone zone, DateTime endsOn, String healthCode,
                    Map<String, DateTime> events, DateTime now, Set<String> userDataGroups) {
        this.studyId = studyId;
        this.clientInfo = clientInfo;
        this.zone = zone;
        this.endsOn = endsOn;
        this.healthCode = healthCode;
        this.events = events;
        this.now = now;
        this.userDataGroups = (userDataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userDataGroups);
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
        return userDataGroups;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, clientInfo, zone, endsOn, healthCode, events, now, userDataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && Objects.equals(zone, other.zone) &&
                Objects.equals(clientInfo, other.clientInfo) && 
                Objects.equals(healthCode, other.healthCode) && Objects.equals(events, other.events) && 
                Objects.equals(studyId, other.studyId) && Objects.equals(now, other.now) && 
                Objects.equals(userDataGroups, other.userDataGroups));
    }

    @Override
    public String toString() {
        return "ScheduleContext [studyId=" + studyId + ", clientInfo=" + clientInfo + ", zone=" + zone + ", endsOn=" + 
                endsOn + ", events=" + events + ", userDataGroups=" + userDataGroups + "]";
    }
    
    public static class Builder {
        private StudyIdentifier studyId;
        private ClientInfo clientInfo;
        private DateTimeZone zone;
        private DateTime endsOn;
        private Map<String,DateTime> events;
        private String healthCode;
        private DateTime now;
        private Set<String> userDataGroups;
        
        public Builder withStudyIdentifier(String studyId) {
            if (studyId != null) {
                this.studyId = new StudyIdentifierImpl(studyId);    
            }
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        public Builder withStudyIdentifier(StudyIdentifier studyId) {
            this.studyId = studyId;
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
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            this.userDataGroups = userDataGroups;
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            this.studyId = context.studyId;
            this.clientInfo = context.clientInfo;
            this.zone = context.zone;
            this.endsOn = context.endsOn;
            this.events = context.events;
            this.healthCode = context.healthCode;
            this.now = context.now;
            this.userDataGroups = context.userDataGroups;
            return this;
        }
        
        public ScheduleContext build() {
            if (now == null) {
                now = (zone == null) ? DateTime.now() : DateTime.now(zone);
            }
            ScheduleContext context = new ScheduleContext(studyId, clientInfo, zone, endsOn, healthCode, events, now, userDataGroups);
            // Not validating here. There are many tests to confirm that the scheduler will work with different 
            // time windows, but the validator ensures the context object is within the declared allowable
            // time window. This is validated in ScheduledActivityService.
            //Validate.nonEntityThrowingException(VALIDATOR, context);
            return context;
        }
    }
    
}
