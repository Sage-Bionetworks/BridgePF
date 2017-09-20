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
    private final JsonNode metadata;
    private final String phoneInfo;
    private final String schemaId;
    private final Integer schemaRevision;
    private final DateTime surveyCreatedOn;
    private final String surveyGuid;

    /** Private constructor. To construct, use builder. */
    private HealthDataSubmission(String appVersion, DateTime createdOn, JsonNode data, JsonNode metadata, String phoneInfo,
            String schemaId, Integer schemaRevision, DateTime surveyCreatedOn, String surveyGuid) {
        this.appVersion = appVersion;
        this.createdOn = createdOn;
        this.data = data;
        this.metadata = metadata;
        this.phoneInfo = phoneInfo;
        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
        this.surveyCreatedOn = surveyCreatedOn;
        this.surveyGuid = surveyGuid;
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

    /**
     * Metadata fields for this record, as submitted by the app. This corresponds with the
     * uploadMetadataFieldDefinitions configured in the study.
     */
    public JsonNode getMetadata() {
        return metadata;
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
    public Integer getSchemaRevision() {
        return schemaRevision;
    }

    /** If the health data is a survey response, this is the createdOn timestamp that specifies the survey version. */
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getSurveyCreatedOn() {
        return surveyCreatedOn;
    }

    /** If the health data is a survey response, this is the GUID that specifies the survey. */
    public String getSurveyGuid() {
        return surveyGuid;
    }

    /** Builder for health data submission. */
    public static class Builder {
        private String appVersion;
        private DateTime createdOn;
        private JsonNode data;
        private JsonNode metadata;
        private String phoneInfo;
        private String schemaId;
        private Integer schemaRevision;
        private DateTime surveyCreatedOn;
        private String surveyGuid;

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

        /** @see HealthDataSubmission#getMetadata */
        public Builder withMetadata(JsonNode metadata) {
            this.metadata = metadata;
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
        public Builder withSchemaRevision(Integer schemaRevision) {
            this.schemaRevision = schemaRevision;
            return this;
        }

        /** @see HealthDataSubmission#getSurveyCreatedOn */
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withSurveyCreatedOn(DateTime surveyCreatedOn) {
            this.surveyCreatedOn = surveyCreatedOn;
            return this;
        }

        /** @see HealthDataSubmission#getSurveyGuid */
        public Builder withSurveyGuid(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }

        /**
         * Builds but does not validate the HealthDataSubmission. HealthDataSubmission is validated by
         * HealthDataSubmissionValidator.
         */
        public HealthDataSubmission build() {
            return new HealthDataSubmission(appVersion, createdOn, data, metadata, phoneInfo, schemaId, schemaRevision,
                    surveyCreatedOn, surveyGuid);
        }
    }
}
