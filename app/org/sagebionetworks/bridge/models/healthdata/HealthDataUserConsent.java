package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeToStringSerializer;
import org.sagebionetworks.bridge.json.JodaDateTimeDeserializer;

/**
 * User consent metadata stored with the health data record. We store this in the Health Data Record table, so we can
 * avoid doing a bunch of extra calls to other tables during export.
 */
@JsonDeserialize(builder = HealthDataUserConsent.Builder.class)
public class HealthDataUserConsent {
    private final boolean hasUserConsented;
    private final DateTime userConsentedOn;
    private final boolean isUserSharingData;
    private final boolean hasUserSignedMostRecentConsent;

    private HealthDataUserConsent(boolean hasUserConsented, DateTime userConsentedOn, boolean isUserSharingData,
            boolean hasUserSignedMostRecentConsent) {
        this.hasUserConsented = hasUserConsented;
        this.userConsentedOn = userConsentedOn;
        this.isUserSharingData = isUserSharingData;
        this.hasUserSignedMostRecentConsent = hasUserSignedMostRecentConsent;
    }

    @JsonProperty("hasUserConsented")
    public boolean hasUserConsented() {
        return hasUserConsented;
    }

    @JsonProperty("isUserSharingData")
    public boolean isUserSharingData() {
        return isUserSharingData;
    }

    @JsonSerialize(using = DateTimeToStringSerializer.class)
    public DateTime getUserConsentedOn() {
        return userConsentedOn;
    }

    @JsonProperty("hasUserSignedMostRecentConsent")
    public boolean hasUserSignedMostRecentConsent() {
        return hasUserSignedMostRecentConsent;
    }

    public static class Builder {
        private boolean hasUserConsented;
        private DateTime userConsentedOn;
        private boolean isUserSharingData;
        private boolean hasUserSignedMostRecentConsent;

        @JsonProperty("hasUserConsented")
        public Builder withUserConsented(boolean hasUserConsented) {
            this.hasUserConsented = hasUserConsented;
            return this;
        }

        @JsonDeserialize(using = JodaDateTimeDeserializer.class)
        public Builder withUserConsentedOn(DateTime userConsentedOn) {
            this.userConsentedOn = userConsentedOn;
            return this;
        }

        @JsonProperty("isUserSharingData")
        public Builder withUserSharingData(boolean isUserSharingData) {
            this.isUserSharingData = isUserSharingData;
            return this;
        }

        @JsonProperty("hasUserSignedMostRecentConsent")
        public Builder withUserSignedMostRecentConsent(boolean hasUserSignedMostRecentConsent) {
            this.hasUserSignedMostRecentConsent = hasUserSignedMostRecentConsent;
            return this;
        }

        public HealthDataUserConsent build() {
            return new HealthDataUserConsent(hasUserConsented, userConsentedOn, isUserSharingData,
                    hasUserSignedMostRecentConsent);
        }
    }
}
