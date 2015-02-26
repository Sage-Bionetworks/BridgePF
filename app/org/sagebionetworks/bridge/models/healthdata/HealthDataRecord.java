package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.LocalDate;

/** This class represents health data and associated metadata. */
@BridgeTypeName("HealthData")
@JsonDeserialize(as = DynamoHealthDataRecord.class)
public interface HealthDataRecord extends BridgeEntity {
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

    /** Struct containing information about user consent and sharing options. */
    HealthDataUserConsent getUserConsentMetadata();

    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();
}
