package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

/** Health data submitted by the user. */
@JsonDeserialize(builder = HealthDataSubmission.Builder.class)
public class HealthDataSubmission implements BridgeEntity {
    private final String appVersion;
    private final DateTime createdOn;
    private final JsonNode data;
    private final String phoneInfo;
    private final String schemaId;
    private final int schemaRevision;

    /** Private constructor. To construct, use builder. */
    private HealthDataSubmission(String appVersion, DateTime createdOn, JsonNode data, String phoneInfo,
            String schemaId, int schemaRevision) {
        this.appVersion = appVersion;
        this.createdOn = createdOn;
        this.data = data;
        this.phoneInfo = phoneInfo;
        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
    }

    /**
     * App version, as reported by the app. Generally in the form "version 1.0.0, build 2". Must be 48 chars or less.
     */
    public String getAppVersion() {
        return appVersion;
    }

    /** When the health data was created, as reported by the app. */
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return createdOn;
    }

    /** Health data, as key-value pairs corresponding to the schema fields. */
    public JsonNode getData() {
        return data;
    }

    /** Phone info, for example "iPhone9,3" or "iPhone 5c (GSM)". Must be 48 chars or less. */
    public String getPhoneInfo() {
        return phoneInfo;
    }

    /** Schema ID with which to process this health data. */
    public String getSchemaId() {
        return schemaId;
    }

    /** Schema revision with which to process this health data. */
    public int getSchemaRevision() {
        return schemaRevision;
    }

    /** Builder for health data submission. */
    public static class Builder {
        private String appVersion;
        private DateTime createdOn;
        private JsonNode data;
        private String phoneInfo;
        private String schemaId;
        private int schemaRevision;

        /** @see HealthDataSubmission#getAppVersion */
        public Builder withAppVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        /** @see HealthDataSubmission#getCreatedOn */
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        /** @see HealthDataSubmission#getData */
        public Builder withData(JsonNode data) {
            this.data = data;
            return this;
        }

        /** @see HealthDataSubmission#getPhoneInfo */
        public Builder withPhoneInfo(String phoneInfo) {
            this.phoneInfo = phoneInfo;
            return this;
        }

        /** @see HealthDataSubmission#getSchemaId */
        public Builder withSchemaId(String schemaId) {
            this.schemaId = schemaId;
            return this;
        }

        /** @see HealthDataSubmission#getSchemaRevision */
        public Builder withSchemaRevision(int schemaRevision) {
            this.schemaRevision = schemaRevision;
            return this;
        }

        /**
         * Builds but does not validate the HealthDataSubmission. HealthDataSubmission is validated by
         * HealthDataSubmissionValidator.
         */
        public HealthDataSubmission build() {
            return new HealthDataSubmission(appVersion, createdOn, data, phoneInfo, schemaId, schemaRevision);
        }
    }
}
