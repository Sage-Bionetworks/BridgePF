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
 * that verifies the four required values (studyId, initialTimeZone, endsOn and healthCode) are present.
 * 
 * @see org.sagebionetworks.bridge.validators.ScheduleContextValidator
 */
public final class ScheduleContext {
    
    private final DateTimeZone initialTimeZone;
    private final DateTimeZone requestTimeZone;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final DateTime now;
    private final DateTime accountCreatedOn;
    private final int minimumPerSchedule;
    private final CriteriaContext criteriaContext;
    
    private ScheduleContext(DateTimeZone initialTimeZone, DateTimeZone requestTimeZone, DateTime endsOn,
            Map<String, DateTime> events, DateTime now, int minimumPerSchedule, DateTime accountCreatedOn,
            CriteriaContext criteriaContext) {
        this.initialTimeZone = initialTimeZone;
        this.requestTimeZone = requestTimeZone;
        this.endsOn = endsOn;
        this.events = events;
        this.now = now;
        this.minimumPerSchedule = minimumPerSchedule;
        this.accountCreatedOn = accountCreatedOn;
        this.criteriaContext = criteriaContext;
    }
    
    /**
     * The initial time zone of the user, the first time that user contacts the server for activities. This allows the 
     * scheduler to interpret events against the user's initial time zone.
     * @return
     */
    public DateTimeZone getInitialTimeZone() {
        return initialTimeZone;
    }

    /**
     * The time zone of the current request, sent to the server as a UTC offset. This is the time zone that local 
     * date and time schedules should use when returning timestamps. For example, a recurring activity at 02:00 PM,
     * if requested for the time zone -05:00, will return a timestamp of "YYYY-MM-DDT14:00:00.000-05:00". In 
     * effect this makes the timestamp a local date and time value.
     */
    public DateTimeZone getRequestTimeZone() {
        return requestTimeZone;
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
     * Now in the user's initial time zone. This will be used to calculate the times of recurring 
     * activities.
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
        return Objects.hash(initialTimeZone, requestTimeZone, endsOn, events, now, accountCreatedOn, minimumPerSchedule, criteriaContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && 
                Objects.equals(initialTimeZone, other.initialTimeZone) &&
                Objects.equals(requestTimeZone, other.requestTimeZone) &&
                Objects.equals(events, other.events) && 
                Objects.equals(now, other.now) &&
                Objects.equals(minimumPerSchedule, other.minimumPerSchedule) &&
                Objects.equals(accountCreatedOn, other.accountCreatedOn) && 
                Objects.equals(criteriaContext, other.criteriaContext));
    }

    @Override
    public String toString() {
        return "ScheduleContext [initialTimeZone=" + initialTimeZone + ", requestTimeZone=" + requestTimeZone
                + ", endsOn=" + endsOn + ", events=" + events + ", now=" + now + ", accountCreatedOn="
                + accountCreatedOn + ", minimumPerSchedule=" + minimumPerSchedule + ", criteriaContext="
                + criteriaContext + "]";
    }


    public static class Builder {
        private DateTimeZone initialTimeZone;
        private DateTimeZone requestTimeZone;
        private DateTime endsOn;
        private int daysAhead;
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
        public Builder withInitialTimeZone(DateTimeZone initialTimeZone) {
            this.initialTimeZone = initialTimeZone;
            return this;
        }
        public Builder withRequestTimeZone(DateTimeZone requestTimeZone) {
            this.requestTimeZone = requestTimeZone;
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
        public Builder withDaysAhead(int daysAhead) {
            this.daysAhead = daysAhead;
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            withInitialTimeZone(context.initialTimeZone);
            withRequestTimeZone(context.requestTimeZone);
            withEndsOn(context.endsOn);
            withEvents(context.events);
            withNow(context.now);
            withMinimumPerSchedule(context.minimumPerSchedule);
            withAccountCreatedOn(context.accountCreatedOn);
            contextBuilder.withContext(context.criteriaContext);
            return this;
        }
        
        public ScheduleContext build() {
            // ScheduleContext is validated to have the correct values:
            // - requestTimeZone is required (when validated)
            // - now is advisable, but still not required
            // - endsOn OR daysAhead needs to be supplied to calculate endsOn from now
            if (initialTimeZone == null) {
                initialTimeZone = requestTimeZone;
            }
            if (requestTimeZone != null) {
                if (now == null) {
                    now = DateTime.now(requestTimeZone);
                } else {
                    now = now.withZone(requestTimeZone);    
                }
                if (endsOn == null) {
                    endsOn = now.plusDays(daysAhead).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59)
                            .withMillisOfSecond(999);
                } else {
                    endsOn = endsOn.withZone(requestTimeZone);
                }
            }
            return new ScheduleContext(initialTimeZone, requestTimeZone, endsOn, events, now, minimumPerSchedule,
                    accountCreatedOn, contextBuilder.build());
        }
    }
}
