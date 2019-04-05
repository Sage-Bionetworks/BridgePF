package org.sagebionetworks.bridge.models.accounts;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AccountSummary {
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Phone phone;
    private final String externalId;
    private final Map<String,String> externalIds;
    private final String id;
    private final DateTime createdOn;
    private final AccountStatus status;
    private final StudyIdentifier studyIdentifier;
    private final Set<String> substudyIds;
    
    @JsonCreator
    public AccountSummary(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
            @JsonProperty("email") String email, @JsonProperty("phone") Phone phone,
            @JsonProperty("externalId") String externalId, @JsonProperty("externalIds") Map<String, String> externalIds,
            @JsonProperty("id") String id, @JsonProperty("createdOn") DateTime createdOn,
            @JsonProperty("status") AccountStatus status,
            @JsonProperty("studyIdentifier") StudyIdentifier studyIdentifier,
            @JsonProperty("substudyIds") Set<String> substudyIds) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.externalId = externalId;
        this.externalIds = externalIds;
        this.id = id;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
        this.status = status;
        this.studyIdentifier = studyIdentifier;
        this.substudyIds = substudyIds;
    }
    
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Phone getPhone() {
        return phone;
    }
    
    public String getExternalId() {
        return externalId;
    }
    
    public Map<String, String> getExternalIds() {
        return externalIds;
    }
    
    public String getId() {
        return id;
    }

    public DateTime getCreatedOn() {
        return createdOn;
    }

    public AccountStatus getStatus() {
        return status;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }
    
    public Set<String> getSubstudyIds() {
        return substudyIds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email, phone, externalId, externalIds, id, createdOn, status,
                studyIdentifier, substudyIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSummary other = (AccountSummary) obj;
        return Objects.equals(firstName, other.firstName) && Objects.equals(lastName, other.lastName)
                && Objects.equals(email, other.email) && Objects.equals(phone, other.phone)
                && Objects.equals(externalId, other.externalId) && Objects.equals(externalIds, other.externalIds)
                && Objects.equals(createdOn, other.createdOn) && Objects.equals(status, other.status)
                && Objects.equals(id, other.id) && Objects.equals(studyIdentifier, other.studyIdentifier)
                && Objects.equals(substudyIds, other.substudyIds);
    }
    
    // no toString() method as the information is sensitive.
}
