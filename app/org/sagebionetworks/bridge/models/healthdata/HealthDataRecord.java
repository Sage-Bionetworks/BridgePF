package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import java.util.Map;
import java.util.Set;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.SharingScope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/** This class represents health data and associated metadata. */
@BridgeTypeName("HealthData")
@JsonDeserialize(as = DynamoHealthDataRecord.class)
public interface HealthDataRecord extends BridgeEntity {
    DateTimeFormatter TIME_ZONE_FORMATTER = DateTimeFormat.forPattern("Z");
    ObjectWriter PUBLIC_RECORD_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
                    SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));

    enum ExporterStatus {
        NOT_EXPORTED, SUCCEEDED
    }

    /** Convenience method to instantiate a HealthDataRecord. */
    static HealthDataRecord create() {
        return new DynamoHealthDataRecord();
    }

    /**
     * App version, as reported by the app. Generally in the form "version 1.0.0, build 2". Must be 48 chars or less.
     */
    String getAppVersion();

    /** @see #getAppVersion */
    void setAppVersion(String appVersion);

    /**
     * The timestamp at which this health data was created (recorded on the client), in milliseconds since 1970-01-01
     * (start of epoch).
     */
    Long getCreatedOn();

    /** @see #getCreatedOn */
    void setCreatedOn(Long createdOn);

    /** Time zone of the createdOn timestamp, expressed as a 4-digit string with sign. Examples: "-0800", "+0900" */
    String getCreatedOnTimeZone();

    /** @see #getCreatedOnTimeZone */
    void setCreatedOnTimeZone(String createdOnTimeZone);

    /** Health data, in JSON format. */
    JsonNode getData();

    /** @see #getData */
    void setData(JsonNode data);

    /** Health code of the user contributing the health data. */
    String getHealthCode();

    /** @see #getHealthCode */
    void setHealthCode(String healthCode);

    /** Unique identifier for the health data record. */
    String getId();

    /** @see #getId */
    void setId(String id);

    /** Miscellaneous metadata associated with this record. This may vary with schema. */
    JsonNode getMetadata();

    /** @see #getMetadata */
    void setMetadata(JsonNode metadata);

    /** Phone info, for example "iPhone9,3" or "iPhone 5c (GSM)". Must be 48 chars or less. */
    String getPhoneInfo();

    /** @see #getPhoneInfo */
    void setPhoneInfo(String phoneInfo);

    /**
     * Attachment ID (S3 key) that contains the raw data. This is the unencrypted zip file for uploads, or JSON blob
     * for directly submitted records.
     */
    String getRawDataAttachmentId();

    /** @see #getRawDataAttachmentId */
    void setRawDataAttachmentId(String rawDataAttachmentId);

    /** Schema ID of the health data. */
    String getSchemaId();

    /** {@inheritDoc} */
    void setSchemaId(String schemaId);

    /** Revision number of the schema of the health data. */
    int getSchemaRevision();

    /** @see #getSchemaRevision */
    void setSchemaRevision(int schemaRevision);

    /** Study ID that the health data record lives in. */
    String getStudyId();

    /** @see #getStudyId */
    void setStudyId(String studyId);

    /** Calendar date the health data was uploaded. This is generally filled in by the Bridge server. */
    LocalDate getUploadDate();

    /** @see #getUploadDate */
    void setUploadDate(LocalDate uploadDate);

    /** ID of the upload this health data record was built from, if applicable. */
    String getUploadId();

    /** @see #getUploadId */
    void setUploadId(String uploadId);

    /**
     * When the data was uploaded to Bridge in epoch milliseconds. Used as an index for hourly and on-demand exports.
     */
    Long getUploadedOn();

    /** @see #getUploadedOn */
    void setUploadedOn(Long uploadedOn);

    /**
     * Metadata fields for this record, as submitted by the app. This corresponds with the
     * uploadMetadataFieldDefinitions configured in the study.
     */
    JsonNode getUserMetadata();

    /** @see #getUserMetadata */
    void setUserMetadata(JsonNode userMetadata);

    /** Whether this record should be shared with all researchers, only study researchers, or not at all. */
    SharingScope getUserSharingScope();

    /** @see #getUserSharingScope */
    void setUserSharingScope(SharingScope userSharingScope);
    
    /**
     * An external identifier that relates this record to other external health data records (analogous to the internal
     * healthCode).
     */
    String getUserExternalId();

    /** @see #getUserExternalId */
    void setUserExternalId(String userExternalId);

    /**
     * The data groups assigned to the user submitting this health data. This set will be null if there are no data 
     * groups assigned to the user.
     */
    Set<String> getUserDataGroups();

    /** @see #getUserDataGroups() */
    void setUserDataGroups(Set<String> userDataGroups);
    
    /**
     * The substudies assigned to the user, and the optional external ID being used for each assignment, if any.
     * The keys of this map are substudy IDs, and the values are either the associated external ID, or an empty 
     * string if there is no associated external ID.
     */
    Map<String,String> getUserSubstudyMemberships();
    
    /** @see #getUserSubstudyMemberships() */
    void setUserSubstudyMemberships(Map<String,String> userSubstudyMemberships);

    /** Error messages related to upload validation. Only generated if UploadValidationStrictness is set to REPORT. */
    String getValidationErrors();

    /** @see #getValidationErrors */
    void setValidationErrors(String validationErrors);

    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();

    /** @see #getVersion */
    void setVersion(Long version);

    /**
     * Used to get the Bridge-Exporter's status of if that tasks' records are submitted to Synapse
     * Only two values: NOT_EXPORTED and SUCCEEDED
     */
    ExporterStatus getSynapseExporterStatus();

    /** @see #getSynapseExporterStatus */
    void setSynapseExporterStatus(ExporterStatus synapseExporterStatus);
}
