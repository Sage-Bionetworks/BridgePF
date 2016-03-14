package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * This record combines a consent signature with the user consent record and the 
 * study consent version signed by the participant. It also includes a healthCode, because 
 * we would need all this information in order to comply with an audit of study consents, 
 * but we do not currently expose this information through the UI.
 * 
 * Because we may need to return this object through the API, it has been written to be 
 * serialized correctly.
 */
@JsonDeserialize(builder=UserConsentHistory.Builder.class)
public final class UserConsentHistory {
    private final String healthCode;
    private final SubpopulationGuid subpopulationGuid;
    private final long consentCreatedOn;
    private final String name;
    private final String birthdate;
    private final String imageData;
    private final String imageMimeType;
    private final long signedOn;
    private final Long withdrewOn;
    private final boolean hasSignedActiveConsent;
    
    private UserConsentHistory(String healthCode, SubpopulationGuid subpopGuid, long consentCreatedOn, String name,
            String birthdate, String imageData, String imageMimeType, long signedOn, Long withdrewOn,
            boolean hasSignedActiveConsent) {
        this.healthCode = healthCode;
        this.subpopulationGuid = subpopGuid;
        this.consentCreatedOn = consentCreatedOn;
        this.name = name;
        this.birthdate = birthdate;
        this.imageData = imageData;
        this.imageMimeType = imageMimeType;
        this.signedOn = signedOn;
        this.withdrewOn = withdrewOn;
        this.hasSignedActiveConsent = hasSignedActiveConsent;
    }

    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    public String getSubpopulationGuid() {
        return subpopulationGuid.getGuid();
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }

    public String getName() {
        return name;
    }

    public String getBirthdate() {
        return birthdate;
    }

    public String getImageData() {
        return imageData;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getSignedOn() {
        return signedOn;
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getWithdrewOn() {
        return withdrewOn;
    }

    public boolean isHasSignedActiveConsent() {
        return hasSignedActiveConsent;
    }
    
    public static class Builder {
        private String healthCode;
        private SubpopulationGuid subpopGuid;
        private long consentCreatedOn;
        private String name;
        private String birthdate;
        private String imageData;
        private String imageMimeType;
        private long signedOn;
        private Long withdrewOn;
        private boolean hasSignedActiveConsent;
        
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withSubpopulationGuid(SubpopulationGuid guid) {
            this.subpopGuid = guid;
            return this;
        }
        @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
        public Builder withConsentCreatedOn(long consentCreatedOn) {
            this.consentCreatedOn = consentCreatedOn;
            return this;
        }
        public Builder withName(String name) {
            this.name = name;
            return this;
        }
        public Builder withBirthdate(String birthdate) {
            this.birthdate = birthdate;
            return this;
        }
        public Builder withImageData(String imageData) {
            this.imageData = imageData;
            return this;
        }
        public Builder withImageMimeType(String imageMimeType) {
            this.imageMimeType = imageMimeType;
            return this;
        }
        @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
        public Builder withSignedOn(long signedOn) {
            this.signedOn = signedOn;
            return this;
        }
        @JsonDeserialize(using = DateTimeToLongDeserializer.class)
        public Builder withWithdrewOn(Long withdrewOn) {
            this.withdrewOn = withdrewOn;
            return this;
        }
        public Builder withHasSignedActiveConsent(boolean hasSignedActiveConsent) {
            this.hasSignedActiveConsent = hasSignedActiveConsent;
            return this;
        }
        public UserConsentHistory build() {
            // validation not necessary, this is only created internally.
            return new UserConsentHistory(healthCode, subpopGuid, consentCreatedOn, name,
                    birthdate, imageData, imageMimeType, signedOn, withdrewOn, hasSignedActiveConsent);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(healthCode, subpopulationGuid, consentCreatedOn, name, birthdate, imageData, imageMimeType,
                signedOn, withdrewOn, hasSignedActiveConsent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        UserConsentHistory other = (UserConsentHistory) obj;
        return (Objects.equals(healthCode, other.healthCode) &&
                Objects.equals(subpopulationGuid, other.subpopulationGuid) &&
                Objects.equals(consentCreatedOn, other.consentCreatedOn) &&
                Objects.equals(name, other.name) &&
                Objects.equals(birthdate, other.birthdate) &&
                Objects.equals(imageData, other.imageData) &&
                Objects.equals(imageMimeType, other.imageMimeType) &&
                Objects.equals(signedOn, other.signedOn) &&
                Objects.equals(withdrewOn, other.withdrewOn) &&
                Objects.equals(hasSignedActiveConsent, other.hasSignedActiveConsent));
    }
    
    @Override
    public String toString() {
        return String.format("UserConsentHistory [healthCode=[REDACTED], subpopulationGuid=%s, consentCreatedOn=%s, name=[REDACTED], birthdate=[REDACTED], imageData=[REDACTED], imageMimeType=%s, signedOn=%s, withdrewOn=%s, hasSignedActiveConsent=%s]",
            subpopulationGuid, consentCreatedOn, imageMimeType, signedOn, withdrewOn, hasSignedActiveConsent);
    }
    
}
