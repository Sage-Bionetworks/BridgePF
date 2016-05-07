package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import java.util.Set;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** This class represents health data and associated metadata. */
@BridgeTypeName("HealthData")
@JsonDeserialize(as = DynamoHealthDataRecord.class)
public interface HealthDataRecord extends BridgeEntity {
    ObjectWriter PUBLIC_RECORD_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
                    SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));

    /**
     * The timestamp at which this health data was created (recorded on the client), in milliseconds since 1970-01-01
     * (start of epoch).
     */
    Long getCreatedOn();

    /** Health data, in JSON format. */
    JsonNode getData();

    /** Health code of the user contributing the health data. */
    String getHealthCode();

    /** Unique identifier for the health data record. */
    String getId();

    /** Miscellaneous metadata associated with this record. This may vary with schema. */
    JsonNode getMetadata();

    /** Schema ID of the health data. */
    String getSchemaId();

    /** Revision number of the schema of the health data. */
    int getSchemaRevision();

    /** Study ID that the health data record lives in. */
    String getStudyId();

    /** Calendar date the health data was uploaded. This is generally filled in by the Bridge server. */
    LocalDate getUploadDate();

    /** ID of the upload this health data record was built from, if applicable. */
    String getUploadId();

    /**
     * When the data was uploaded to Bridge in epoch milliseconds. Used as an index for hourly and on-demand exports.
     */
    Long getUploadedOn();

    /** Whether this record should be shared with all researchers, only study researchers, or not at all. */
    ParticipantOption.SharingScope getUserSharingScope();
    
    /**
     * An external identifier that relates this record to other external health data records (analogous to the internal
     * healthCode).
     */
    String getUserExternalId();

    /**
     * The data groups assigned to the user submitting this health data. This set will be null if there are no data 
     * groups assigned to the user.
     */
    Set<String> getUserDataGroups();
    
    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();
}
