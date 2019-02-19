package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Information about the criteria and access times of requests from a specific user. Useful for 
 * support and troubleshooting, and potentially useful to show the filtering that is occurring on 
 * the server to researchers in the researcher UI.
 */
public final class RequestInfo {

    private final String userId;
    private final ClientInfo clientInfo;
    private final String userAgent;
    private final List<String> languages;
    private final Set<String> userDataGroups;
    private final Set<String> userSubstudyIds;
    private final DateTime activitiesAccessedOn;
    private final DateTime signedInOn;
    private final DateTime uploadedOn;
    private final DateTimeZone timeZone;
    private final StudyIdentifier studyIdentifier;

    @JsonCreator
    private RequestInfo(@JsonProperty("userId") String userId, @JsonProperty("clientInfo") ClientInfo clientInfo,
            @JsonProperty("userAgent") String userAgent, @JsonProperty("languages") List<String> languages,
            @JsonProperty("userDataGroups") Set<String> userDataGroups,
            @JsonProperty("userSubstudyIds") Set<String> userSubstudyIds,
            @JsonProperty("activitiesAccessedOn") DateTime activitiesAccessedOn,
            @JsonProperty("signedInOn") DateTime signedInOn, @JsonProperty("uploadedOn") DateTime uploadedOn,
            @JsonProperty("timeZone") DateTimeZone timeZone,
            @JsonProperty("studyIdentifier") StudyIdentifier studyIdentifier) {
        this.userId = userId;
        this.clientInfo = clientInfo;
        this.userAgent = userAgent;
        this.languages = languages;
        this.userDataGroups = userDataGroups;
        this.userSubstudyIds = userSubstudyIds;
        this.activitiesAccessedOn = activitiesAccessedOn;
        this.signedInOn = signedInOn;
        this.uploadedOn = uploadedOn;
        this.timeZone = timeZone;
        this.studyIdentifier = studyIdentifier;
    }

    public String getUserId() {
        return userId;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    public String getUserAgent() {
        return userAgent;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }

    public Set<String> getUserSubstudyIds() {
        return userSubstudyIds;
    }
    
    @JsonSerialize(using=DateTimeSerializer.class)
    public DateTime getActivitiesAccessedOn() {
        return (activitiesAccessedOn == null) ? null : activitiesAccessedOn.withZone(timeZone);
    }

    @JsonSerialize(using=DateTimeSerializer.class)
    public DateTime getSignedInOn() {
        return (signedInOn == null) ? null : signedInOn.withZone(timeZone);
    }
    
    @JsonSerialize(using=DateTimeSerializer.class)
    public DateTime getUploadedOn() {
        return (uploadedOn == null) ? null : uploadedOn.withZone(timeZone);
    }
    
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getActivitiesAccessedOn(), clientInfo, userAgent, languages, getSignedInOn(),
                userDataGroups, userSubstudyIds, userId, timeZone, uploadedOn, studyIdentifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        RequestInfo other = (RequestInfo) obj;
        return Objects.equals(getActivitiesAccessedOn(), other.getActivitiesAccessedOn()) &&
               Objects.equals(clientInfo, other.clientInfo) &&
               Objects.equals(userAgent, other.userAgent) &&
               Objects.equals(languages, other.languages) &&
               Objects.equals(getSignedInOn(), other.getSignedInOn()) && 
               Objects.equals(getUploadedOn(), other.getUploadedOn()) &&
               Objects.equals(userDataGroups, other.userDataGroups) && 
               Objects.equals(userSubstudyIds, other.userSubstudyIds) && 
               Objects.equals(userId, other.userId) && 
               Objects.equals(timeZone, other.timeZone) && 
               Objects.equals(studyIdentifier, other.studyIdentifier);
    }
    
    @Override
    public String toString() {
        return "RequestInfo [userId=" + userId + ", userAgent=" + userAgent + ", languages=" + languages
                + ", userDataGroups=" + userDataGroups + ", userSubstudyIds=" + userSubstudyIds 
                + ", activitiesAccessedOn=" + getActivitiesAccessedOn() + ", signedInOn=" + getSignedInOn() 
                + ", uploadedOn=" + getUploadedOn() + ", timeZone=" + timeZone + ", studyIdentifier=" 
                + studyIdentifier + "]";
    }

    public static class Builder {
        private String userId;
        private ClientInfo clientInfo;
        private String userAgent;
        private List<String> languages;
        private Set<String> userDataGroups;
        private Set<String> userSubstudyIds;
        private DateTime activitiesAccessedOn;
        private DateTime signedInOn;
        private DateTime uploadedOn;
        private DateTimeZone timeZone = DateTimeZone.UTC;
        private StudyIdentifier studyIdentifier;

        public Builder copyOf(RequestInfo requestInfo) {
            if (requestInfo != null) {
                withUserId(requestInfo.getUserId());
                withClientInfo(requestInfo.getClientInfo());
                withUserAgent(requestInfo.getUserAgent());
                withLanguages(requestInfo.getLanguages());
                withUserDataGroups(requestInfo.getUserDataGroups());
                withUserSubstudyIds(requestInfo.getUserSubstudyIds());
                withActivitiesAccessedOn(requestInfo.getActivitiesAccessedOn());
                withSignedInOn(requestInfo.getSignedInOn());
                withUploadedOn(requestInfo.getUploadedOn());
                withTimeZone(requestInfo.getTimeZone());
                withStudyIdentifier(requestInfo.getStudyIdentifier());
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
            if (clientInfo != null) {
                this.clientInfo = clientInfo;
            }
            return this;
        }
        public Builder withUserAgent(String userAgent) {
            if (userAgent != null) {
                this.userAgent = userAgent;
            }
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            if (languages != null) {
                this.languages = languages;
            }
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            if (userDataGroups != null) {
                this.userDataGroups = userDataGroups;
            }
            return this;
        }
        public Builder withUserSubstudyIds(Set<String> userSubstudyIds) {
            if (userSubstudyIds != null) {
                this.userSubstudyIds = userSubstudyIds;
            }
            return this;
        }
        public Builder withActivitiesAccessedOn(DateTime activitiesAccessedOn) {
            if (activitiesAccessedOn != null) {
                this.activitiesAccessedOn = activitiesAccessedOn;
            }
            return this;
        }
        public Builder withSignedInOn(DateTime signedInOn) {
            if (signedInOn != null) {
                this.signedInOn = signedInOn;
            }
            return this;
        }
        public Builder withUploadedOn(DateTime uploadedOn) {
            if (uploadedOn != null) {
                this.uploadedOn = uploadedOn;
            }
            return this;
        }
        public Builder withTimeZone(DateTimeZone timeZone) {
            if (timeZone != null) {
                this.timeZone = timeZone;
            }
            return this;
        }
        public Builder withStudyIdentifier(StudyIdentifier studyIdentifier) {
            if (studyIdentifier != null) {
                this.studyIdentifier = studyIdentifier;
            }
            return this;
        }
        
        public RequestInfo build() {
            return new RequestInfo(userId, clientInfo, userAgent, languages, userDataGroups, userSubstudyIds,
                    activitiesAccessedOn, signedInOn, uploadedOn, timeZone, studyIdentifier);
        }
    }
    
}
