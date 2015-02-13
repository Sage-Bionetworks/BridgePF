package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * This class is used so that whenever another class (such as the upload validation worker apps) needs to create a
 * prototype HealthDataRecord object to save into DynamoDB, this class can be used instead of using the Dynamo
 * implementation directly.
 */
public abstract class HealthDataRecordBuilder {
    private JsonNode data;
    private String healthCode;
    private String id;
    private DateTime measuredTime;
    private JsonNode metadata;
    private String schemaId;
    private LocalDate uploadDate;

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getData */
    public JsonNode getData() {
        return data;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getData */
    public HealthDataRecordBuilder withData(JsonNode data) {
        this.data = data;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getHealthCode */
    public String getHealthCode() {
        return healthCode;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getHealthCode */
    public HealthDataRecordBuilder withHealthCode(String healthCode) {
        this.healthCode = healthCode;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getId */
    public String getId() {
        return id;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getId */
    public HealthDataRecordBuilder withId(String id) {
        this.id = id;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getMeasuredTime */
    public DateTime getMeasuredTime() {
        return measuredTime;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getMeasuredTime */
    public HealthDataRecordBuilder withMeasuredTime(DateTime measuredTime) {
        this.measuredTime = measuredTime;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getMetadata */
    public JsonNode getMetadata() {
        return metadata;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getMetadata */
    public HealthDataRecordBuilder withMetadata(JsonNode metadata) {
        this.metadata = metadata;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getSchemaId */
    public String getSchemaId() {
        return schemaId;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getSchemaId */
    public HealthDataRecordBuilder withSchemaId(String schemaId) {
        this.schemaId = schemaId;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getUploadDate */
    public LocalDate getUploadDate() {
        return uploadDate;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataRecord#getUploadDate */
    public HealthDataRecordBuilder withUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    /**
     * <p>
     * Builds and validates the HealthDataRecord object. This throws an InvalidEntityException if validation fails. See
     * {@link org.sagebionetworks.bridge.validators.HealthDataRecordValidator} for validation preconditions.
     * </p>
     * <p>
     * This builder also adds reasonable defaults to fields that are null or unspecified. Specifically:
     *   <ul>
     *     <li>data and metadata default to an empty ObjectNode</li>
     *     <li>measuredTime defaults to the current time</li>
     *     <li>uploadDate defaults to the current calendar date (as measured in Pacific local time)</li>
     *   </ul>
     * </p>
     */
    public HealthDataRecord build() {
        // default values
        if (data == null) {
            data = BridgeObjectMapper.get().createObjectNode();
        }
        if (metadata == null) {
            metadata = BridgeObjectMapper.get().createObjectNode();
        }
        if (measuredTime == null) {
            measuredTime = DateTime.now();
        }
        if (uploadDate == null) {
            uploadDate = LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE);
        }

        // build and validate
        HealthDataRecord record = buildUnvalidated();
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
        return record;
    }

    /** Builds a HealthDataRecord object. Subclasses should implement this method. */
    protected abstract HealthDataRecord buildUnvalidated();
}
