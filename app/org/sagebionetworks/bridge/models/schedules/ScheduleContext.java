package org.sagebionetworks.bridge.models.schedules;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
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
    
    private final DateTimeZone zone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final DateTime now;
    private final DateTime accountCreatedOn;
    private final int minimumPerSchedule;
    private final CriteriaContext criteriaContext;
    
    private ScheduleContext(DateTimeZone zone, DateTime endsOn, Map<String, DateTime> events, DateTime now,
            int minimumPerSchedule, DateTime accountCreatedOn, CriteriaContext criteriaContext) {
        this.zone = zone;
        this.endsOn = endsOn;
        this.events = events;
        this.now = now;
        this.minimumPerSchedule = minimumPerSchedule;
        this.accountCreatedOn = accountCreatedOn;
        this.criteriaContext = criteriaContext;
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
    
    public int getMinimumPerSchedule() {
        return minimumPerSchedule;
    }
    
    public DateTime getAccountCreatedOn() {
        return accountCreatedOn;
    }
    
    public CriteriaContext getCriteriaContext() {
        return criteriaContext;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(zone, endsOn, events, now, accountCreatedOn, minimumPerSchedule, criteriaContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && Objects.equals(zone, other.zone) &&
                Objects.equals(events, other.events) && Objects.equals(now, other.now) &&
                Objects.equals(minimumPerSchedule, other.minimumPerSchedule) &&
                Objects.equals(accountCreatedOn, other.accountCreatedOn) && 
                Objects.equals(criteriaContext, other.criteriaContext));
    }

    @Override
    public String toString() {
        return "ScheduleContext [zone=" + zone + ", endsOn=" + endsOn + ", events=" + events + ", now=" + now
                + ", accountCreatedOn=" + accountCreatedOn + ", minimumPerSchedule=" + minimumPerSchedule 
                + ", criteriaContext=" + criteriaContext + "]";
    }


    public static class Builder {
        private DateTimeZone zone;
        private DateTime endsOn;
        private Map<String,DateTime> events;
        private int minimumPerSchedule;
        private DateTime now;
        private DateTime accountCreatedOn;
        private CriteriaContext.Builder contextBuilder = new CriteriaContext.Builder();
        
        public Builder withStudyIdentifier(String studyId) {
            contextBuilder.withStudyIdentifier(new StudyIdentifierImpl(studyId));
            return this;
        }
        public Builder withStudyIdentifier(StudyIdentifier studyId) {
            contextBuilder.withStudyIdentifier(studyId);
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            contextBuilder.withHealthCode(healthCode);
            return this;
        }
        public Builder withUserId(String userId) {
            contextBuilder.withUserId(userId);
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            contextBuilder.withClientInfo(clientInfo);
            return this;
        }
        public Builder withMinimumPerSchedule(int minimumPerSchedule) {
            this.minimumPerSchedule = minimumPerSchedule;
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
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            contextBuilder.withUserDataGroups(userDataGroups);
            return this;
        }
        public Builder withNow(DateTime now) {
            this.now = now;
            return this;
        }
        public Builder withAccountCreatedOn(DateTime accountCreatedOn) {
            this.accountCreatedOn = new DateTime(accountCreatedOn, DateTimeZone.UTC);
            return this;
        }
        public Builder withLanguages(LinkedHashSet<String> languages) {
            contextBuilder.withLanguages(languages);
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            withTimeZone(context.zone);
            withEndsOn(context.endsOn);
            withEvents(context.events);
            withNow(context.now);
            withMinimumPerSchedule(context.minimumPerSchedule);
            withAccountCreatedOn(context.accountCreatedOn);
            contextBuilder.withContext(context.criteriaContext);
            return this;
        }
        
        public ScheduleContext build() {
            // pretty much everything else is optional. I would like healthCode to be required, but it's not:
            // we use these selection criteria to select subpopulations on sign up.
            if (now == null) {
                now = (zone == null) ? DateTime.now() : DateTime.now(zone);
            }
            return new ScheduleContext(zone, endsOn, events, now, minimumPerSchedule, accountCreatedOn, contextBuilder.build());
        }
    }
}
