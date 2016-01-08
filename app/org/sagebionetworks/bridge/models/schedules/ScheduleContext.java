package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

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
import com.google.common.collect.ImmutableSet;

/**
 * All the information necessary to convert a schedule into a set of activities, on a given request. 
 * Because some of these values derive from the user, there is a validator that is run on this object 
 * that verifies the four required values (studyId, zone, endsOn and healthCode) are present.
 * 
 * @see org.sagebionetworks.bridge.validators.ScheduleContextValidator
 */
public final class ScheduleContext {
    
    private final StudyIdentifier studyId;
    private final String userId; // for debugging purposes only.
    private final ClientInfo clientInfo;
    private final DateTimeZone zone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final String healthCode;
    private final DateTime now;
    private final Set<String> userDataGroups;
    
    private ScheduleContext(StudyIdentifier studyId, String userId, ClientInfo clientInfo, DateTimeZone zone, DateTime endsOn, String healthCode,
                    Map<String, DateTime> events, DateTime now, Set<String> userDataGroups) {
        this.studyId = studyId;
        this.userId = userId;
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
     * The user id (not the health code). Only used for debugging purposes.
     * @return
     */
    public String getUserId() {
        return userId;
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
        return Objects.hash(studyId, userId, clientInfo, zone, endsOn, healthCode, events, now, userDataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && Objects.equals(zone, other.zone) &&
                Objects.equals(userId, other.userId) &&
                Objects.equals(clientInfo, other.clientInfo) && 
                Objects.equals(healthCode, other.healthCode) && Objects.equals(events, other.events) && 
                Objects.equals(studyId, other.studyId) && Objects.equals(now, other.now) && 
                Objects.equals(userDataGroups, other.userDataGroups));
    }

    @Override
    public String toString() {
        return "ScheduleContext [studyId=" + studyId + ", userId=" + userId + ", clientInfo=" + clientInfo + ", zone="
                + zone + ", endsOn=" + endsOn + ", events=" + events + ", userDataGroups=" + userDataGroups + "]";
    }
    
    public static class Builder {
        private StudyIdentifier studyId;
        private String userId;
        private ClientInfo clientInfo;
        private DateTimeZone zone;
        private DateTime endsOn;
        private Map<String,DateTime> events;
        private String healthCode;
        private DateTime now;
        private Set<String> userDataGroups;
        
        public Builder withUser(User user) {
            if (user != null) {
                this.userId = user.getId();
                this.healthCode = user.getHealthCode();
                this.studyId = new StudyIdentifierImpl(user.getStudyKey());
                this.userDataGroups = user.getDataGroups();                
            }
            return this;
        }
        public Builder withStudyIdentifier(String studyId) {
            if (studyId != null) {
                this.studyId = new StudyIdentifierImpl(studyId);    
            }
            return this;
        }
        public Builder withUserId(String userId) {
            if (userId != null) {
                this.userId = userId;    
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
        public Builder withNow(DateTime now) {
            this.now = now;
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            this.studyId = context.studyId;
            this.userId = context.userId;
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
            checkNotNull(studyId, "studyId cannot be null");
            // pretty much everything else is optional. I would like healthCode to be required, but it's not:
            // we use these selection criteria to select subpopulations on sign up.
            if (now == null) {
                now = (zone == null) ? DateTime.now() : DateTime.now(zone);
            }
            if (clientInfo == null) {
                clientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            return new ScheduleContext(studyId, userId, clientInfo, zone, endsOn, healthCode, events, now, userDataGroups);
        }
    }
    
}
